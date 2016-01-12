package com.conveyal.r5.api.util;

import com.conveyal.r5.profile.StreetPath;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a response model class which holds data that will be serialized and returned to the client.
 * It is not used internally in routing.
 */
public class ProfileOption {
    //Transit leg of a journey
    public List<TransitSegment> transit;
    //Part of journey from start to transit (or end) @notnull
    public List<StreetSegment> access;
    //Part of a journey between transit stops (transfers)
    public List<StreetSegment> middle;
    //Part of journey from transit to end
    public List<StreetSegment> egress;
    //Connects all the trip part to a trip at specific time with specific modes of transportation
    public List<Itinerary> itinerary;
    //Time stats for this part of a journey @notnull
    public Stats stats = new Stats();
    //Text description of this part of a journey @notnull
    public String summary;
    public List<Fare> fares;

    @Override public String toString() {
        return "ProfileOption{" +
            " transit=\n   " + transit +
            ", access=\n   " + access +
            ", egress=\n   " + egress +
            ", stats=" + stats +
            ", summary='" + summary + '\'' +
            ", fares=" + fares +
            '}' + "\n";
    }

    /**
     * Initializes access and itinerary since those are non null.
     *
     * Other fields are initialized as needed
     */
    public ProfileOption() {
        access = new ArrayList<>();
        itinerary = new ArrayList<>();
    }

    /** Make a human readable text summary of this option.
     * There are basically four options:
     * - Direct non-transit routes which are named "Non-transit options"
     * - Transit without transfers only on one route which are named "routes [route num]"
     * - Transit without transfers with multiple route options which are named "routes [route num]/[route num]..."
     * - Transit with transfers which are named "routes "[route nun]/[route num], [route num]/[route num] via [STATION NAME]"
     *
     * "/" in name designates OR and "," AND station name is a station where transfer is needed
     * */
    public String generateSummary() {
        if (transit == null || transit.isEmpty()) {
            return "Non-transit options";
        }
        List<String> vias = Lists.newArrayList();
        List<String> routes = Lists.newArrayList();
        for (TransitSegment segment : transit) {
            List<String> routeShortNames = Lists.newArrayList();
            for (Route rs : segment.routes) {
                String routeName = rs.shortName == null ? rs.longName : rs.shortName;
                routeShortNames.add(routeName);
            }
            routes.add(Joiner.on("/").join(routeShortNames));
            vias.add(segment.to.name);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("routes ");
        sb.append(Joiner.on(", ").join(routes));
        if (!vias.isEmpty()) vias.remove(vias.size() - 1);
        if (!vias.isEmpty()) {
            sb.append(" via ");
            sb.append(Joiner.on(", ").join(vias));
        }
        return sb.toString();
    }

    /**
     * Adds direct routing without transit at provided fromTimeDate
     *
     * This also creates itinerary and adds streetSegment to access list
     * @param streetSegment
     * @param fromTimeDateZD
     */
    public void addDirect(StreetSegment streetSegment, ZonedDateTime fromTimeDateZD) {
        Itinerary itinerary = new Itinerary(streetSegment, access.size(), fromTimeDateZD);
        access.add(streetSegment);
        this.itinerary.add(itinerary);
    }

    public void addTransit(TransitSegment transitSegment) {
        if (transit == null) {
            transit = new ArrayList<>(5);
        }
        transit.add(transitSegment);
    }

    public int addAccess(StreetSegment streetSegment) {
        access.add(streetSegment);
        return (access.size() - 1);
    }

    public int addEgress(StreetSegment streetSegment) {
        if (egress == null) {
            egress = new ArrayList<>();
        }
        egress.add(streetSegment);
        return (egress.size() - 1);
    }

    public void addItinerary(Integer accessIdx, Integer egressIdx,
        List<TransitJourneyID> transitJourneyIDs, ZoneId timeZone) {
        Itinerary itinerary = new Itinerary();
        itinerary.transfers = transitJourneyIDs.size();

        //FIXME: actual waiting time
        itinerary.waitingTime = 0;
        //TODO: middle part duration
        itinerary.walkTime = access.get(accessIdx).duration+egress.get(egressIdx).duration;
        itinerary.distance = access.get(accessIdx).distance+egress.get(egressIdx).distance;
        ZonedDateTime transitStart = transit.get(0).segmentPatterns.get(transitJourneyIDs.get(0).pattern).fromDepartureTime.get(transitJourneyIDs.get(0).time);
        itinerary.startTime = transitStart.minusSeconds(access.get(accessIdx).duration);
        int lastTransit = transitJourneyIDs.size()-1;
        ZonedDateTime transitStop = transit.get(lastTransit).segmentPatterns.get(transitJourneyIDs.get(lastTransit).pattern).toArrivalTime.get(transitJourneyIDs.get(lastTransit).time);
        itinerary.endTime = transitStop.plusSeconds(egress.get(egressIdx).duration);
        //FIXME: this ignores waiting in transfers
        itinerary.transitTime = (int) Duration.between(transitStart, transitStop).getSeconds();
        PointToPointConnection pointToPointConnection = new PointToPointConnection(accessIdx, egressIdx, transitJourneyIDs);
        itinerary.addConnection(pointToPointConnection);
        this.itinerary.add(itinerary);

    }
}
