/*
 * Copyright 2016 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

var log = require("./log.js").logger();
var util = require('util');
var events = require('events');
var rhea = require('rhea');
var artemis = require('./artemis.js');
var myevents = require('./events.js');
var myutils = require('./utils.js');
var plimit = require('p-limit');
var crypto = require('crypto');
var uuidv5 = require('uuid/v5');
var clone = require('clone');

function BrokerController(event_sink, config) {
    events.EventEmitter.call(this);
    this.check_in_progress = false;
    this.post_event = event_sink || function (event) { log.debug('event: %j', event); };
    this.serial_sync = myutils.serialize(this._sync_addresses_and_forwarders.bind(this));
    this.addresses_synchronized = false;
    this.busy_count = 0;
    this.retrieve_count = 0;
    this.last_retrieval = undefined;
    this.excluded_types = undefined;
    this.config = config ? config : process.env;
    this.global_max_size = this.read_global_max_size();
    this.root_address_settings = {};
};

util.inherits(BrokerController, events.EventEmitter);

BrokerController.prototype.start_polling = function (poll_frequency) {
    this.polling = setInterval(this.check_broker_addresses.bind(this), poll_frequency || 5000);//poll broker stats every 5 secs by default
};

function type_filter(ignored) {
    return function (type) {
        return ignored.indexOf(type) >= 0;
    }
}

BrokerController.prototype.connect = function (options) {
    this.excluded_types = type_filter(['subscription']);
    var container = rhea.create_container();
    container.on('connection_open', this.on_connection_open.bind(this));
    return container.connect(options);
};

BrokerController.prototype.close = function () {
    if (this.polling) {
        clearInterval(this.polling);
        this.polling = undefined;
    }
    return this.broker.close();
};

BrokerController.prototype.on_connection_open = function (context) {
    var self = this;
    this.broker = new artemis.Artemis(context.connection);
    this.id = context.connection.container_id;
    this.initializeGlobalMaxSizeFromBrokerIfUnset();
    self.broker.getAddressSettings('#').then((root_address_settings) => {
        self.root_address_settings = root_address_settings;
        self.check_broker_addresses().then(() => {
            log.info('[%s] broker controller ready', self.id);
            self.emit('ready');
        }).catch((e) => {
            log.error("[%s] failed to check_broker_addresses", self.id, e);
        });
    }).catch((e) => {
        log.error("[%s] failed to get root settings", self.id, e);
    });
};

BrokerController.prototype.set_connection = function (connection) {
    var self = this;
    this.broker = new artemis.Artemis(connection);
    this.id = connection.container_id;
    this.initializeGlobalMaxSizeFromBrokerIfUnset();
    log.info('[%s] broker controller ready', this.id);

    return new Promise((resolve, reject) => {
        self.broker.getAddressSettings('#').then((root_address_settings) => {
            self.root_address_settings = root_address_settings;
            resolve();
        }).catch(reject);
    });
}

BrokerController.prototype.initializeGlobalMaxSizeFromBrokerIfUnset = function () {
    var self = this;
    if (this.global_max_size <= 0) {
        this.broker.getGlobalMaxSize().then(function (global_max_size) {
            log.info('[%s] global_max_size pulled from broker (%d)', self.id, global_max_size);
            //Broker defaults global_max_size to 1/2 the jvm memory (1/4 of the container memory).  We need it to be 1/8th container memory.
            self.global_max_size = (global_max_size / 2);
        }).catch((e) => {
            log.error("[%s] failed to get global_max_size from the broker", self.id, e);
        });
    }
}

BrokerController.prototype.addresses_defined = function (addresses) {
    this.addresses = addresses.reduce(function (map, a) { map[a.address] = a; return map; }, {});
    return this.check_broker_addresses();
};

BrokerController.prototype.sync_addresses = function (addresses) {
    this.addresses = addresses.reduce(function (map, a) { map[a.address] = a; return map; }, {});
    return this.serial_sync();
};

BrokerController.prototype.sync_addresssettings = function (addresses) {
    var limit = plimit(250);
    let update_address_setting_fn = limit.bind(null, this.update_address_setting.bind(this));

    return Promise.all(addresses.map(update_address_setting_fn));
};

BrokerController.prototype.close_connection = function (connection) {
    return this.broker.closeConnection(connection.id);
};

function total() {
    var result = 0;
    for (var i in arguments) {
        if (arguments[i]) result += arguments[i];
    }
    return result;
}

function transform_queue_stats(queue) {
    return {
        receivers: queue.consumers,
        senders: 0,/*add this later*/
        depth: queue.messages,
        messages_in: queue.enqueued,
        messages_out: total(queue.acknowledged, queue.expired, queue.killed),
        propagated: 100,
        shards: [queue],
        outcomes: {
            egress: {
                links: [],
                accepted: queue.acknowledged,
                unsettled: queue.delivered,
                rejected: queue.killed
            },
            ingress: {
                links: [],
                accepted: queue.enqueued
            }
        }
    };
}

function sum(field, list) {
    return util.isArray(list) ? list.map(function (o) { return o[field] || 0; }).reduce(function (a, b) { return a + b; }, 0) : 0;
}

function transform_topic_stats(topic) {
    return {
        receivers: topic.subscription_count,
        senders: 0,/*add this later*/
        depth: topic.enqueued,
        /*this isn't really correct; what we want is the total number of messages sent to the topic (independent of the number of subscribers)*/
        messages_in: sum('enqueued', topic.subscriptions),
        messages_out: sum('acknowledged', topic.subscriptions),
        propagated: 100,
        shards: [topic],
        outcomes: {
            egress: {
                links: [],
                accepted: sum('acknowledged', topic.subscriptions),
                unsettled: sum('delivered', topic.subscriptions),
                rejected: sum('killed', topic.subscriptions)
            },
            ingress: {
                links: [],
                accepted: topic.enqueued
            }
        }
    };
}

function transform_address_stats(address) {
    return address.type === 'queue' || address.type === 'deadletter' ? transform_queue_stats(address) : transform_topic_stats(address);
}

BrokerController.prototype.transform_connection_stats = function(raw) {
    var addressSpace = process.env.ADDRESS_SPACE;
    var addressSpaceNamespace = process.env.ADDRESS_SPACE_NAMESPACE;
    var addressSpaceType = process.env.ADDRESS_SPACE_TYPE;

    var uuid = generateStableUuid(addressSpaceNamespace, addressSpace, raw.connectionID, raw.clientAddress);

    return {
        id: raw.connectionID,
        addressSpace: addressSpace,
        addressSpaceNamespace: addressSpaceNamespace,
        addressSpaceType: addressSpaceType,
        uuid: uuid,
        host: raw.clientAddress,
        container: 'not available',
        creationTimestamp: raw.creationTime ? Math.floor(raw.creationTime / 1000) : 0,
        user: raw.sessions.length ? raw.sessions[0].principal : '',
        senders: [],
        receivers: [],

        close: () => {return this.broker.closeConnection(raw.connectionID)}
    };
}

function generateStableUuid() {
    var hash = crypto.createHash('sha256');
    for (var i = 0, j = arguments.length; i < j; i++){
        var argument = arguments[i];
        if (argument) {
            hash.update(argument);
        }
    }
    var ba = [];
    ba.push(...hash.digest().slice(0, 16));
    const ns = "3751f842-240e-48b9-89b5-5b47f04e931b";
    return uuidv5(ns, ba);
}


function transform_producer_stats(raw) {
    return {
        uuid: generateStableUuid(raw.connectionID, raw.sessionID, raw.destination),
        name: undefined,
        connection_id: raw.connectionID,
        address: raw.destination,
        deliveries: raw.msgSent,
        deliveryCount: raw.msgSent,
        lastUpdated: Date.now()
    };
}

function transform_consumer_stats(raw) {
    return {
        uuid: generateStableUuid(raw.connectionID, raw.sessionID, raw.consumerID),
        name: raw.consumerID,
        connection_id: raw.connectionID,
        address: raw.queueName,//mapped to address in later step
        deliveries: 0,//TODO: not yet available from broker

        lastUpdated: Date.now()
    };
}

function index_by(list, field) {
    return list.reduce(function (map, item) { map[item[field]] = item; return map; }, {});
}

function collect_by_connection(conns, links, name) {
    links.forEach(function (link) {
        var conn = conns[link.connection_id];
        if (conn) {
            conn[name].push(link);
        }
    });
}

function collect_by_address(addresses, links, name, count) {
    links.forEach(function (link) {
        var address = addresses[link.address];
        if (address) {
            address.outcomes[name].links.push(link);
            if (count) address[count] = address.outcomes[name].links.length;
        }
    });
}

function is_not_internal(conn) {
    return conn.user !== undefined && conn.user.match('^agent.[a-z0-9]+$') == null; //Can't get properties or anything else on which to base decision yet
}

function same_address_settings(orig_address_settings, new_address_settings) {
    const keys1 = Object.keys(orig_address_settings);
    const keys2 = Object.keys(new_address_settings);

    if (keys1.length !== keys2.length) {
        return false;
    }

    for (let key of keys1) {
        if (orig_address_settings[key] !== new_address_settings[key]) {
            return false;
        }
    }
    return true;
}

BrokerController.prototype.retrieve_stats = function () {
    var self = this;
    if (this.broker !== undefined) {
        return Promise.all([
            this.broker.getAllAddressData(),
            this.broker.listConnectionsWithSessions(),
            this.broker.listProducers(),
            this.broker.listConsumers()]).then(function (results) {
                var address_stats = {};
                for (var name in results[0].index) {
                    address_stats[name] = transform_address_stats(results[0].index[name]);
                }
                var connection_stats = index_by(results[1].map(self.transform_connection_stats, self).filter(is_not_internal), 'id');
                var senders = results[2].map(transform_producer_stats);
                var receivers = results[3].map(transform_consumer_stats);
                receivers.forEach(function (r) {
                    var q = results[0].reverse_index[r.address];
                    if (q) {
                        r.address = q;
                    }
                });

                collect_by_connection(connection_stats, senders, 'senders');
                collect_by_connection(connection_stats, receivers, 'receivers');

                collect_by_address(address_stats, senders, 'ingress', 'senders');
                collect_by_address(address_stats, receivers, 'egress');

                for(var c in connection_stats) {
                    connection_stats[c].messages_in = connection_stats[c].senders.reduce(function (total, sender) { return total + sender.deliveries; }, 0);
                    // there is currently insufficient data exposed by the consumer to compute a messages_out
                }

                self.retrieve_count++;
                var now = Date.now();
                if (self.last_retrieval === undefined) {
                    log.info('[%s] broker stats retrieved (%d)', self.id, self.retrieve_count);
                } else {
                    var interval = now - self.last_retrieval;
                    if (interval >= 60000) {
                        log.info('[%s] broker stats retrieved %d times in last %d secs', self.id, self.retrieve_count, Math.round(interval/1000));
                        self.retrieve_count = 0;
                    }
                }
                self.emit('address_stats_retrieved', address_stats);
                self.emit('connection_stats_retrieved', connection_stats);
            }).catch(function (error) {
                log.error('[%s] error retrieving stats: %s', self.id, error);
            });
    } else {
        return Promise.resolve();
    }
};

function same_address(a, b) {
    return b !== undefined && a.address === b.address && a.type === b.type;
}

function difference(a, b, equivalent) {
    var diff = {};
    for (var k in a) {
	if (!equivalent(a[k], b[k])) {
	    diff[k] = a[k];
	}
    }
    return diff;
}

function address_and_type(object) {
    return {address: object.address, type: object.type};
}

function values(map) {
    return Object.keys(map).map(function (key) { return map[key]; });
}

function exclude_subscriptions(type) {
    return type === 'subscription';
}

function excluded_addresses(address) {
    return address === '!!GLOBAL_DLQ' || address === 'DLQ' || address === 'ExpiryQueue' || address === 'activemq.notifications' || address === 'activemq.management' || address.startsWith("!!HEALTH_CHECK_BROKER");
}

function is_temp_queue(a) {
    return a.type === 'queue' &&
        (a.temporary || (a.name.startsWith('activemq.management.tmpreply') && a.durable === false &&
            a.maxConsumers === a.consumers && a.consumers > 0 /* ignore in-use response queues */))
}

function is_wildcard_sub(a) {
    return a.type === 'topic' && (a.name.includes("#") || a.name.includes("+") || a.name.includes("/"));
}

function toplevel_address_exists(a, addresses_in) {
    for (var name in addresses_in) {
        if (a.name.startsWith(name)) {
            return true;
        }
    }
    return false;
}

function is_durable_subscription(a) {
    return a.durable && !a.purgeOnNoConsumers;
}

/**
 * Translate from the address details we get back from artemis to the
 * structure used for the definition, for easier comparison.
 */
BrokerController.prototype._translate = function (addresses_in, excluded_names, excluded_types) {
    var deadletters = values(this.addresses).filter((o) => o.type === 'deadletter');
    var addresses_out = {};
    for (var name in addresses_in) {
        if (excluded_names && excluded_names(name)) continue;
        var a = addresses_in[name];
        if (excluded_types && excluded_types(a.type)) continue;
        if (is_temp_queue(a)) {
            log.debug('ignoring temp queue %s', a.name);
            continue;
        }
        if (is_wildcard_sub(a) && toplevel_address_exists(a, addresses_in)) {
            log.debug("Ignoring wildcard sub");
            continue;
        }
        if (a.name) {
            if (deadletters.find(dla => dla.address === a.name)) {
                a.type = "deadletter";
            }
            addresses_out[name] = {address:a.name, type: a.type};
        } else {
            log.warn('Skipping address with no name: %j', a);
        }
    }
    return addresses_out;
}

BrokerController.prototype.update_address_setting = function (a) {
    var self = this;
    var name = a.address;
    return self.broker.getAddressSettings(name).then(function (orig_settings) {
        return self.get_address_settings(a).then(function (new_settings) {
            if (new_settings) {
                if (!same_address_settings(orig_settings, new_settings)) {
                    log.info('[%s] Adding/updating address settings: %s', self.id, name);
                    return self.broker.addAddressSettings(name, new_settings);
                } else {
                    log.debug('[%s] Address settings match for %s: not updating', self.id, name);
                    return Promise.resolve();
                }
            } else if (!same_address_settings(orig_settings, self.root_address_settings)) {
                // Settings were previously applied but now no longer required.  To remove them first we
                // reset the address settings to the root values then remove the record.
                log.info('[%s] Removing address settings: %s', self.id, name);
                return self.broker.addAddressSettings(name, self.root_address_settings).finally(() => {
                    return self.broker.removeAddressSettings(name);
                });
            } else {
                // No settings required (and none currently applied).
                return Promise.resolve();
            }
        }).catch(function (error) {
            log.error('[%s] Failed to create new address setting %s: %s', self.id, name, error);
            return Promise.reject(error);
        });
    }).catch(function (error) {
        log.error('[%s] Failed to retrieve broker address setting: %s', self.id, name, error);
        return Promise.reject(error);
    });
}

BrokerController.prototype.delete_address = function (a) {
    var self = this;
    if (a.type === 'queue' || a.type === 'deadletter') {
        log.info('[%s] Deleting %s "%s"...', self.id, a.type, a.address);
        return self.broker.destroyQueue(a.address).then(function () {
            log.info('[%s] Deleted %s "%s"', self.id, a.type, a.address);
            self.post_event(myevents.address_delete(a));
        }).catch(function (error) {
            log.error('[%s] Failed to delete %s %s: %s', self.id, a.type, a.address, error);
            return self.broker.deleteAddress(a.address).then(function () {
                log.info('[%s] Deleted %s address %s', self.id, a.type, a.address);
            }).catch(function (error) {
                log.error('[%s] Failed to delete %s address %s: %s', self.id, a.type, a.address, error);
                self.post_event(myevents.address_failed_delete(a, error));
            });
        });
    } else if (a.type === 'topic') {
        log.info('[%s] Deleting topic "%s"...', self.id, a.address);
        return self.broker.deleteAddressAndBindings(a.address).then(function () {
            log.info('[%s] Deleted topic "%s"', self.id, a.address);
            self.post_event(myevents.address_delete(a));
        }).catch(function (error) {
            log.error('[%s] Failed to delete topic %s: %s', self.id, a.address, error);
            self.post_event(myevents.address_failed_delete(a, error));
        });
    } else if (a.type === 'subscription') {
        log.info('[%s] Deleting subscription "%s"...', self.id, a.address);
        return self.broker.destroyQueue(a.address).then(function () {
            log.info('[%s] Deleted subscription "%s"', self.id, a.address);
            self.post_event(myevents.address_delete(a));
        }).catch(function (error) {
            log.error('[%s] Failed to delete subscription %s: %s', self.id, a.address, error);
        });
    }
};

BrokerController.prototype.delete_address_and_settings = function (a) {
    var self = this;
    log.info('[%s] Deleting address-settings for "%s"...', self.id, a.address);
    return self.broker.removeAddressSettings(a.address).then(function() {
        log.info('[%s] Deleted address-settings for "%s"', self.id, a.address);
        return self.delete_address(a);
    }).catch(function (error) {
        log.error('[%s] Failed to delete address setting for "%s": %s', self.id, a.address, error);
        return self.delete_address(a);
    });
};

BrokerController.prototype.delete_addresses = function (addresses) {
    var limit = plimit(250);
    let delete_fn = limit.bind(null, this.delete_address_and_settings.bind(this));
    return Promise.all(addresses.map(delete_fn));
};

BrokerController.prototype.create_address = function (a) {
    var self = this;
    if (a.type === 'queue' || a.type === 'deadletter') {
        log.info('[%s] Creating %s "%s"...', self.id, a.type, a.address);
        return self.broker.createQueue(a.address).then(function () {
            log.info('[%s] Created %s "%s"', self.id, a.type, a.address);
            self.post_event(myevents.address_create(a));
        }).catch(function (error) {
            log.error('[%s] Failed to create %s "%s": %s', self.id, a.type, a.address, error);
            self.post_event(myevents.address_failed_create(a, error));
        });
    } else if (a.type === 'topic') {
        log.info('[%s] Creating topic "%s"', self.id, a.address);
        return self.broker.createAddress(a.address, {multicast:true}).then(function () {
            log.info('[%s] Created topic "%s"', self.id, a.address);
            self.post_event(myevents.address_create(a));
        }).catch(function (error) {
            log.error('[%s] Failed to create topic "%s": %s', self.id, a.address, error);
            self.post_event(myevents.address_failed_create(a, error));
        });
    } else if (a.type === 'subscription') {
        var maxConsumers = 1;
        if (a.subscription !== undefined && a.subscription.maxConsumers !== undefined) {
            maxConsumers = a.subscription.maxConsumers;
        }
        log.info('[%s] Creating subscription "%s" on "%s" with max consumers "%d"...', self.id, a.address, a.topic, maxConsumers);
        return self.broker.createSubscription(a.address, a.topic, maxConsumers).then(function () {
            log.info('[%s] Created subscription "%s" on "%s" with max consumers "%d"', self.id, a.address, a.topic, maxConsumers);
            self.post_event(myevents.address_create(a));
        }).catch(function (error) {
            log.error('[%s] Failed to create subscription "%s" on "%s": %s', self.id, a.address, a.topic, error);
            self.post_event(myevents.address_failed_create(a, error));
        });
    }
};

BrokerController.prototype.create_addresses = function (addresses) {
    var limit = plimit(250);
    let create_fn = limit.bind(null, this.create_address.bind(this));
    return Promise.all(addresses.map(create_fn));
};

BrokerController.prototype.check_broker_addresses = function () {
    if (this.broker !== undefined && this.addresses !== undefined) {
        if (!this.check_in_progress) {
            this.busy_count = 0;
            this.check_in_progress = true;
            var self = this;
            return this._sync_broker_addresses().then(function () {
                return self.retrieve_stats().then(function () {
                    self.check_in_progress = false;
                }).catch( function (error) {
                    log.error('[%s] error retrieving stats: %s', self.id, error);
                    self.check_in_progress = false;
                });
            }).catch (function (error) {
                log.error('[%s] error syncing addresses: %s', self.id, error);
                self.check_in_progress = false;
            });
        } else {
            if (++(this.busy_count) % 10 === 0) {
                log.info('Unable to check broker, check already in progress (%d)', this.busy_count);
            }
            return Promise.resolve();
        }
    } else {
        log.info('Unable to check broker (broker %s defined, addresses %s defined)', this.broker === undefined ? 'is not' : 'is', this.addresses === undefined ? 'is not' : 'is');
        return Promise.resolve();
    }
};

// Only used in tests
BrokerController.prototype._sync_addresses = function (addresses) {
    this.addresses = addresses.reduce(function (map, a) { map[a.address] = a; return map; }, {});
    return this._sync_broker_addresses();
};

BrokerController.prototype._set_sync_status = function (stale, missing) {
    var b = (stale === 0 && missing === 0);
    if (this.addresses_synchronized !== b) {
        if (b) {
            log.info('[%s] addresses are synchronized', this.id);
            this.emit('synchronized');
        } else {
            log.info('[%s] addresses synchronizing (%d to delete, %d to create)', this.id, stale, missing);
        }
    }
    this.addresses_synchronized = b;
}

BrokerController.prototype._sync_broker_addresses = function (retry) {
    var self = this;
    return this.broker.listAddresses().then(function (results) {
        var addrSettings = self.sync_addresssettings(values(self.addresses).filter((o) => o.type === 'subscription' || o.type === 'queue' || o.type === 'deadletter' || o.type === 'topic'));
        var actual = self._translate(results, excluded_addresses, self.excluded_types);

        var stale = values(difference(actual, self.addresses, same_address));
        var missing = values(difference(self.addresses, actual, same_address));
        if (log.isDebugEnabled()) {
            log.debug('[%s] checking addresses, desired=%j, actual=%j => delete %j and create %j', self.id, values(self.addresses).map(address_and_type), values(actual),
                stale.map(address_and_type), missing.map(address_and_type));
        }
        self._set_sync_status(stale.length, missing.length);
        return addrSettings.then(() => {
                return self.delete_addresses(stale).then(
                    function () {
                        return self.create_addresses(missing.filter(function (o) {
                            return o.type !== 'subscription'
                        })).then(function () {
                            return self.create_addresses(missing.filter(function (o) {
                                return o.type === 'subscription'
                            })).then(function () {
                                return retry ? true : self._sync_broker_addresses(true);
                            });
                        });
                    });


            }
        );

    }).catch(function (e) {
        log.error('[%s] failed to retrieve addresses: %s', self.id, e);
        throw e;
    });
};

BrokerController.prototype.destroy_connector = function (connector) {
    var self = this;
    log.info('[%s] Deleting connector for "%s"...', self.id, connector.name);
    return self.broker.destroyConnectorService(connector.name).then(function() {
        log.info('[%s] Deleted connector for "%s"', self.id, connector.name);
    }).catch(function (error) {
        log.error('[%s] Failed to delete connector for "%s": %s', self.id, connector.name, error);
    });
};

BrokerController.prototype.create_connector = function (connector) {
    var self = this;
    log.info('[%s] Creating connector "%j"...', self.id, connector);
    return self.broker.createConnectorService(connector).then(function () {
        log.info('[%s] Created connector for "%s"', self.id, connector.name);
    }).catch(function (error) {
        log.error('[%s] Failed to create connector for "%s": %s', self.id, connector.name, error);
    });
};

BrokerController.prototype.destroy_connectors = function (connectors) {
    return Promise.all(connectors.map(this.destroy_connector.bind(this)));
};

BrokerController.prototype.create_connectors = function (connectors) {
    return Promise.all(connectors.map(this.create_connector.bind(this)));
};

function connectors_of_interest(name) {
    return !name.startsWith("$override");
}

function connector_compare(a, b) {
    return myutils.string_compare(a.name, b.name);
}

BrokerController.prototype._sync_broker_forwarders = function () {
    var self = this;
    var desired = [];
    for (var address in this.addresses) {
        var address_type = this.addresses[address].type;
        // We will only check queues and subscriptions for forwarders
        if (address_type === "queue" || address_type === "subscription") {
            var address_name = this.addresses[address].name;
            var forwarders = this.addresses[address].forwarders;
            if (forwarders !== undefined) {
                for (var fwdidx in forwarders) {
                    var forwarder = forwarders[fwdidx];
                    var forwarder_name = address_name + "." + forwarder.name + "." + forwarder.direction;
                    // Only create forwarder for subscriptions if the direction is outward
                    if (address_type === "queue" || (address_type === "subscription" && forwarder.direction === "out")) {
                        var sourceAddress = forwarder.direction === "out" ? (address_type === "subscription" ? this.addresses[address].topic + "::" + address : address) : forwarder.remoteAddress;
                        var targetAddress = forwarder.direction === "out" ? forwarder.remoteAddress : address;
                        var forwarder_entry = {
                            name: forwarder_name,
                            clusterId: address_name,
                            containerId: address_name,
                            linkName: forwarder_name,
                            targetAddress: targetAddress,
                            sourceAddress: sourceAddress,
                            direction: forwarder.direction,
                            consumerPriority: forwarder.priority,
                            useModifiedForTransientDeliveryErrors: 'FWD_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS' in this.config ? this.config.FWD_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS : true,
                            treatRejectAsUnmodifiedDeliveryFailed: 'FWD_TREAT_REJECT_AS_UNMODIFIED_DELIVERY_FAILED' in this.config ? this.config.FWD_TREAT_REJECT_AS_UNMODIFIED_DELIVERY_FAILED : true
                        };
                        desired.push(forwarder_entry);
                    }
                }
            }
        }
    }
    log.debug("desired connectors: %j", desired);
    return this.broker.getConnectorServices().then(function (results) {
        var actual = results.filter(connectors_of_interest);
        actual.sort();
        actual = actual.map(function (name) {
            return {name: name}
        });

        var difference = myutils.changes(actual, desired, connector_compare);
        if (difference) {
            log.info('[%s] %d connectors missing, %d to be removed', self.id, difference.added.length, difference.removed.length);
            return self.destroy_connectors(difference.removed).then(function () {
                return self.create_connectors(difference.added);
            });
        } else {
            log.info('[%s] all connectors exist', self.id);
        }
    }).catch(function (e) {
        log.error('[%s] failed to retrieve connectors: %s', self.id, e);
        throw e;
    });
};

BrokerController.prototype._sync_addresses_and_forwarders = function () {
    return this._sync_broker_addresses().then(this._sync_broker_forwarders());
};


BrokerController.prototype.generate_address_settings = function (address, global_max_size) {
    if (address.status) {
        var addressSettings = clone(this.root_address_settings);
        var upd = 0;
        if (address.status.planStatus && address.status.planStatus.resources && address.status.planStatus.resources.broker > 0 && (address.type === 'subscription' || address.type === 'queue')) {
            var planStatus = address.status.planStatus;

            var r = planStatus.resources.broker;
            var p = planStatus.partitions;
            var allocation = (r && p) ? (r / p) : (r) ? r : undefined;
            if (allocation) {
                var maxSizeBytes = Math.round(allocation * global_max_size);
                if ('PAGE' === addressSettings.addressFullMessagePolicy && addressSettings.pageSizeBytes > 0) {
                    // Artemis business rule that maxSizeBytes >= pageSizeBytes
                    maxSizeBytes = Math.max(maxSizeBytes, addressSettings.pageSizeBytes);
                } else {
                    addressSettings.pageSizeBytes = -1;
                }
                addressSettings.maxSizeBytes = maxSizeBytes;
                upd++;
            }
        }
        if (address.status.messageTtl && (address.type === 'topic' || address.type === 'queue' || address.type === 'deadletter')) {
            if (address.status.messageTtl.minimum) {
                addressSettings.minExpiryDelay = address.status.messageTtl.minimum;
                upd++;
            }
            if (address.status.messageTtl.maximum) {
                addressSettings.maxExpiryDelay = address.status.messageTtl.maximum;
                upd++;
            }
        }

        var notSubscription = address.type === 'topic' || address.type === 'queue' || address.type === 'deadletter';
        if (notSubscription) {
            if (address.deadletter) {
                addressSettings.DLA = address.deadletter;
                // Specify default redelivery settings by default when using a DLQ. These are based
                // on Artemis defaults.
                addressSettings.maxDeliveryAttempts = 10;
                addressSettings.redeliveryDelay = 0;
                addressSettings.redeliveryMultiplier = 1.0;
                addressSettings.maxRedeliveryDelay = 0;

                upd++;
            }
            if (address.expiry) {
                addressSettings.expiryAddress = address.expiry;
                upd++;
            }
            if (address.status.messageRedelivery) {
                if (address.status.messageRedelivery.maximumDeliveryAttempts) {
                    addressSettings.maxDeliveryAttempts = address.status.messageRedelivery.maximumDeliveryAttempts;
                    upd++;
                }
                if (address.status.messageRedelivery.redeliveryDelay) {
                    addressSettings.redeliveryDelay = address.status.messageRedelivery.redeliveryDelay;
                    upd++;
                }
                if (address.status.messageRedelivery.redeliveryMultiplier) {
                    addressSettings.redeliveryMultiplier = address.status.messageRedelivery.redeliveryMultiplier;
                    upd++;
                }
                if (address.status.messageRedelivery.maximumDeliveryDelay) {
                    addressSettings.maxRedeliveryDelay = address.status.messageRedelivery.maximumDeliveryDelay;
                    upd++;
                }
            }
        }
        return upd ? addressSettings : undefined;
    }
};

BrokerController.prototype.read_global_max_size = function () {
    return this.config.BROKER_GLOBAL_MAX_SIZE ? myutils.parseToBytes(this.config.BROKER_GLOBAL_MAX_SIZE) : 0;
};

BrokerController.prototype.get_address_settings = function (address) {
    var self = this;
    if (self.global_max_size > 0) {
        return Promise.resolve(self.generate_address_settings(address, self.global_max_size));
    } else {
        log.debug('no global max, therefore not applying address settings');
        return Promise.resolve();
    }
};

module.exports.create_controller = function (connection, event_sink) {
    var rcg = connection.properties['qd.route-container-group'];
    var bc = new BrokerController(event_sink);
    if (rcg === 'sharded-topic') {
        //only control subscriptions
        bc.excluded_types = type_filter(['queue', 'topic']);
        log.info('excluding types %j controller for %s (%s)', bc.excluded_types, connection.container_id, rcg);
    }

    return new Promise((resolve, reject) => {
        bc.set_connection(connection).then(() => {
            resolve(bc);
        }).catch(reject);
    });
};

module.exports.create_agent = function (event_sink, polling_frequency) {
    var bc = new BrokerController(event_sink);
    bc.start_polling(polling_frequency);
    return bc;
};

module.exports.BrokerController = BrokerController; // testing
