/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

import React, { useState } from "react";
import { useQuery } from "@apollo/react-hooks";
import { IConnectionLinksResponse } from "schema/ResponseTypes";
import { RETURN_CONNECTION_LINKS } from "graphql-module/queries";
import { Loading } from "use-patternfly";
import {
  ILink,
  ConnectionLinksList
} from "modules/connection-detail/components/ConnectionLinksList/ConnectionLinksList";
import { getFilteredValue } from "utils";
import { ISortBy } from "@patternfly/react-table";
import { POLL_INTERVAL, FetchPolicy } from "constant";
import { EmptyConnectionLinks } from "modules/connection-detail/components";

interface IConnectionDetailContainerProps {
  name: string;
  namespace: string;
  connectionName: string;
  page: number;
  perPage: number;
  setTotalLinks: (value: number) => void;
  filterNames: any[];
  filterAddresses: any[];
  filterRole?: string;
  sortValue?: ISortBy;
  setSortValue: (value: ISortBy) => void;
}
export const ConnectionLinksContainer: React.FunctionComponent<IConnectionDetailContainerProps> = ({
  name,
  namespace,
  connectionName,
  page,
  perPage,
  setTotalLinks,
  filterNames,
  filterAddresses,
  filterRole,
  sortValue,
  setSortValue
}) => {
  const [sortBy, setSortBy] = useState<ISortBy>();
  if (sortValue && sortBy !== sortValue) {
    setSortBy(sortValue);
  }
  const { loading, data } = useQuery<IConnectionLinksResponse>(
    RETURN_CONNECTION_LINKS(
      page,
      perPage,
      filterNames,
      filterAddresses,
      name || "",
      namespace || "",
      connectionName || "",
      sortBy,
      filterRole
    ),
    { pollInterval: POLL_INTERVAL, fetchPolicy: FetchPolicy.NETWORK_ONLY }
  );

  if (loading && !data) return <Loading />;

  const { connections } = data || {
    connections: { total: 0, connections: [] }
  };

  const connection = connections && connections.connections[0];

  const getRows = () => {
    let linkRows: ILink[] = [];
    if (connection && connection.links.total >= 0) {
      setTotalLinks(connection.links.total);
      linkRows = connection.links.links.map(link => ({
        name: link.metadata.name,
        role: link.spec.role,
        address: link.spec.address,
        deliveries: getFilteredValue(link.metrics, "enmasse_deliveries"),
        accepted: getFilteredValue(link.metrics, "enmasse_accepted"),
        rejected: getFilteredValue(link.metrics, "enmasse_rejected"),
        released: getFilteredValue(link.metrics, "enmasse_released"),
        modified: getFilteredValue(link.metrics, "enmasse_modified"),
        presettled: getFilteredValue(link.metrics, "enmasse_presettled"),
        undelivered: getFilteredValue(link.metrics, "enmasse_undelivered")
      }));
    }
    return linkRows;
  };

  const onSort = (_event: any, index: any, direction: any) => {
    setSortBy({ index: index, direction: direction });
    setSortValue({ index: index, direction: direction });
  };

  const linkRows = getRows();

  return (
    <>
      <ConnectionLinksList rows={linkRows} onSort={onSort} sortBy={sortBy} />
      {!linkRows || (linkRows.length <= 0 && <EmptyConnectionLinks />)}
    </>
  );
};
