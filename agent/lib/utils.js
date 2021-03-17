/*
 * Copyright 2017 Red Hat Inc.
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

var rhea = require('rhea');
var util = require('util');
var log = require("./log.js").logger();
var crypto = require('crypto');
var uuidv5 = require('uuid/v5');

module.exports.remove = function (list, predicate) {
    var count = 0;
    for (var i = 0; i < list.length;) {
        if (predicate(list[i])) {
            list.splice(i, 1);
            count++;
        } else {
            i++;
        }
    }
    return count;
};

module.exports.replace = function (list, object, match) {
    for (var i = 0; i < list.length; i++) {
        if (match(list[i])) {
            list[i] = object;
            return true;
        }
    }
    return false;
};

module.exports.merge = function () {
    return Array.prototype.slice.call(arguments).reduce(function (a, b) {
        for (var key in b) {
            a[key] = b[key];
        }
        return a;
    });
}

module.exports.basic_auth = function (request) {
    if (request.headers.authorization) {
        var parts = request.headers.authorization.split(' ');
        if (parts.length === 2 && parts[0].toLowerCase() === 'basic') {
            parts = new Buffer(parts[1], 'base64').toString().split(':');
            return { username: parts[0], password: parts[1] };
        } else {
            throw new Error('Cannot handle authorization header ' + request.headers.authorization);
        }
    }
}

function self(o) {
    return o;
}

module.exports.index = function (a, key, value) {
    var fk = key || self;
    var fv = value || self;
    var m = {};
    a.forEach(function (i) { m[fk(i)] = fv(i); });
    return m;
}

module.exports.values = function (map) {
    var v = [];
    for (var k in map) {
        v.push(map[k]);
    }
    return v;
}

module.exports.separate = function (map, predicate, a, b) {
    for (var k in map) {
        var v = map[k];
        if (predicate(v)) {
            a[k] = v;
        } else {
            b[k] = v;
        }
    }
}

module.exports.difference = function (a, b, equivalent) {
    var diff = {};
    for (var k in a) {
	if (!equivalent(b[k], a[k])) {
	    diff[k] = a[k];
	}
    }
    return diff;
}

module.exports.match_source_address = function (link, address) {
    return link && link.local && link.local.attach && link.local.attach.source
        && link.local.attach.source.value[0].toString() === address;
}

function hash(s) {
    var h = 0;
    for (var i = 0; i < s.length; i++) {
        h = ((h << 5) - h) + s.charCodeAt(i) | 0;
    }
    return h;
};

module.exports.hash = hash;

const MAX_KUBE_NAME = 63/*max allowed*/ - 3/*needed for kube to add stateful set qualifier*/;
module.exports.kubernetes_name = function (name) {
    var clean = name.toLowerCase().replace(/[^a-z0-9\-\.]/g, '');
    if (clean.length > MAX_KUBE_NAME) clean = clean.substring(0, MAX_KUBE_NAME);
    if (clean.charAt(0) === '-' || clean.charAt(0) === '.') clean = clean.substring(1);
    if (clean.charAt(clean.length-1) === '-' || clean.charAt(clean.length-1) === '.') clean = clean.substring(0,clean.length-1);

    var qualifier = rhea.generate_uuid();
    clean += '.' + qualifier;
    return clean;
}

module.exports.serialize = function (f, retry_timeout = 5000) {
    var in_progress = false;
    var pending = false;
    var timeout = null;
    function doIt() {
        if (in_progress) {
            pending = true;
        } else {
            in_progress = true;
            execute();
        }
    }
    function scheduleRetry (error) {
        if (retry_timeout) {
            log.warn("Rescheduling failed serialized func (%s) in %dms.", error, retry_timeout);
            timeout = setTimeout(doIt, retry_timeout);
        }
    }
    function cancelRetry() {
        if (timeout) {
            clearTimeout(timeout);
            timeout = null;
        }
    }
    function execute() {
        try {
            f().then(function () {
                if (pending) {
                    next();
                } else {
                    in_progress = false;
                }
            }).catch(function(error) {
                in_progress = false;
                scheduleRetry(error);
            });
        } catch (error) {
            in_progress = false;
            scheduleRetry(error);
        }
    }
    function next() {
        pending = false;
        setImmediate(execute);
    };
    return function() {
        cancelRetry();
        doIt();
    }
};

module.exports.string_compare = function (a, b) {
    if (a === b) return 0;
    else if (a < b) return -1;
    else return 1;
};

//return changes between two sorted lists
module.exports.changes = function (last, current, compare, unchanged, stringify) {
    let description = stringify || JSON.stringify;
    if (last === undefined) {
        return {
            added: current,
            removed: [],
            modified: [],
            description: util.format('initial %s', description(current))
        };
    } else {
        let d = {
            added: [],
            removed: [],
            modified: []
        };
        let i = 0, j = 0;
        while (i < last.length && j < current.length) {
            switch (compare(last[i], current[j])) {
            case 0:
                //same address, has it changed?
                if (unchanged && !unchanged(last[i], current[j])) {
                    d.modified.push(current[j]);
                }
                i++; j++;
                break;
            case 1:
                //current[j] comes before last[i], therefore it is not in last
                d.added.push(current[j++]);
                break;
            case -1:
                //last[i] comes before current[j], therefore it is not in current
                d.removed.push(last[i++]);
                break;
            }
        }
        while (i < last.length) {
            //remaining items were in last but not in current
            d.removed.push(last[i++]);
        }
        while (j < current.length) {
            //remaining items are in current but not in last
            d.added.push(current[j++]);
        }
        if (d.added.length || d.removed.length || d.modified.length) {
            var parts = [];
            for (let k in d) {
                if (d[k].length) {
                    parts.push(util.format('%s %s', k, description(d[k])));
                }
            }
            d.description = parts.join(', ');
            return d;
        } else {
            return undefined;
        }
    }
};

module.exports.coalesce = function (f, delay, max_delay) {
    var start, scheduled, timeout = undefined;
    var timeout = undefined;

    function fire() {
        start = undefined;
        timeout = undefined;
        f();
    }

    function can_delay() {
        return start && scheduled < (start + max_delay);
    }

    function schedule() {
        timeout = setTimeout(fire, delay);
        scheduled = Date.now() + delay;
    }

    return function () {
        if (timeout) {
            if (can_delay()) {
                clearTimeout(timeout);
                schedule();
            } // else just wait for previously scheduled call
        } else {
            start = Date.now();
            schedule();
        }
    }
};

module.exports.get = function (object, fields, default_value) {
    var o = object;
    for (var i = 0; o && i < fields.length; i++) {
        o = o[fields[i]];
    }
    return o || default_value;
};

var multipliers = {};
multipliers.B = 1;
multipliers.KB = ( multipliers.B * 1024 );
multipliers.MB = ( multipliers.KB * 1024 );
multipliers.GB = ( multipliers.MB * 1024 );
multipliers.TB = ( multipliers.GB * 1024 );
multipliers.PB = ( multipliers.TB * 1024 );
multipliers.EB = ( multipliers.PB * 1024 );
multipliers.ZB = ( multipliers.EB * 1024 );

module.exports.parseToBytes = function (input) {
    var unit = input.toUpperCase().match(/([KMGTPEZ]I?)?B$/);

    if (!unit) {
        log.warn( "parseToBytes input does not contain a supported unit of measurement.  Using 'B': %s", input );
        unit = "B";
    }
    unit = unit[ 0 ].replace( /i/i, "" );

    var value = parseFloat(input);
    if (isNaN(value)) {
        log.error( "parseToBytes input does not contain a numeric value: %s",input );
        return 0;
    }

    return( value * multipliers[unit] );
};

module.exports.same_status_messages = function(a, b) {
    if (a === b) {
        return true;
    } else if (a == null || b == null || a.length !== b.length) {
        return false;
    }

    for (var i in a) {
        if (!b.includes(a[i])) {
            return false;
        }
    }
    return true;
};

module.exports.same_ttl = function (a, b) {
    if (a === b) return true;
    return a && b && a.minimum === b.minimum && a.maximum === b.maximum;
};

module.exports.same_message_redelivery = function (a, b) {
    if (a === b) return true;
    return a && b && a.maximumDeliveryAttempts === b.maximumDeliveryAttempts
        && a.redeliveryDelay === b.redeliveryDelay
        && a.redeliveryDelayMultiplier === b.redeliveryDelayMultiplier
        && a.maximumDeliveryDelay === b.maximumDeliveryDelay;
};

module.exports.description = function description(list, name) {
    const max = 5;
    if (list.length > max) {
        return list.slice(0, max).map(name).join(', ') + ' and ' + (list.length - max) + ' more';
    } else {
        return JSON.stringify(list.map(name));
    }
};

module.exports.generate_stable_uuid = function() {
    var hash = crypto.createHash('sha1');
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
