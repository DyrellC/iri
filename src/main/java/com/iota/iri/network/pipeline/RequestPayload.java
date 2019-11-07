package com.iota.iri.network.pipeline;

import com.iota.iri.network.neighbor.Neighbor;

public class RequestPayload extends Payload {
    private Neighbor originNeighbor;
    private int index;

    public RequestPayload(Neighbor originNeighbor, int index) {
        this.originNeighbor = originNeighbor;
        this.index = index;
    }


    @Override
    public Neighbor getOriginNeighbor(){return originNeighbor;}

    public int getIndex(){return index;}
}
