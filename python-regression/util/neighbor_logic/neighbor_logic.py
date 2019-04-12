import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def check_if_neighbors(nodes):
    """
    This method is used to determine if a node contains the neighbors specified in the steps feature list

    :param nodes: A list of nodes that need to be neighbored with one another
    """
    to_check = nodes

    for to_check_node_index in to_check:
        node_to_check = to_check[to_check_node_index]

        for node_index in nodes:
            node = nodes[node_index]

            if node['address'] is not node_to_check['address']:
                address = node['address']
                current_node_address = node_to_check['address']

                if address not in node_to_check['node_neighbors']:
                    logger.info("Adding neighbor")
                    response = node_to_check['api'].add_neighbors([address])
                    assert response is not None, "No response from {}".format(current_node_address)
                    response = node['api'].add_neighbors([current_node_address])
                    assert response is not None, "No response from {}".format(address)

                    logger.info("Added {} as a neigbor to {}".format(address, current_node_address))


