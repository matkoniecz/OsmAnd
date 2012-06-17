package net.osmand.router;

import java.util.LinkedHashMap;
import java.util.Map;

import net.osmand.binary.RouteDataObject;
import net.osmand.binary.BinaryMapRouteReaderAdapter.RouteRegion;
import net.osmand.binary.BinaryMapRouteReaderAdapter.RouteTypeRule;
import net.osmand.osm.MapUtils;
import net.osmand.router.BinaryRoutePlanner.RouteSegment;

public class GeneralRouter extends VehicleRouter {
	Map<String, Double> highwaySpeed = new LinkedHashMap<String, Double>();
	Map<String, Double> highwayPriorities = new LinkedHashMap<String, Double>();
	Map<String, Double> highwayFuturePriorities = new LinkedHashMap<String, Double>();
	Map<String, Double> avoidElements = new LinkedHashMap<String, Double>();
	Map<String, Double> obstacles = new LinkedHashMap<String, Double>();
	boolean followSpeedLimitations = true;
	boolean restrictionsAware = true;
	boolean onewayAware = true;
	double minDefaultSpeed = 10;
	double maxDefaultSpeed = 10;
	double leftTurn = 0;
	double rightTurn = 0;
	GeneralRouterProfile profile;
	
	public enum GeneralRouterProfile {
		CAR,
		PEDESTRIAN,
		BICYCLE
	}

	@Override
	public boolean acceptLine(RouteDataObject way) {
		if(!highwaySpeed.containsKey(way.getHighway())) {
			return false;
		}
		int[] s = way.getTypes();
		
		for(int i=0; i<s.length; i++) {
			RouteTypeRule r = way.region.quickGetEncodingRule(s[i]);
			String k = r.getTag() + "$" + r.getValue();
			if(avoidElements.containsKey(k)) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public boolean restrictionsAwayre() {
		return restrictionsAware;
	}
	
	@Override
	public double defineObstacle(RouteDataObject road, int point) {
		int[] pointTypes = road.getPointTypes(point);
		if(pointTypes == null) {
			return 0;
		}
		RouteRegion reg = road.region;
		int sz = pointTypes.length;
		for(int i=0; i<sz; i++) {
			RouteTypeRule r = reg.quickGetEncodingRule(pointTypes[i]);
			String key = r.getTag() + "$" + r.getValue();
			Double v = obstacles.get(key);
			if(v != null ){
				return v;
			}
		}
		return 0;
	}
	
	@Override
	public int isOneWay(RouteDataObject road) {
		if (!onewayAware) {
			return 0;
		}
		return super.isOneWay(road);
	}

	@Override
	public double getFutureRoadPriority(RouteDataObject road) {
		String highway = road.getHighway();
		double priority = highway != null && highwayFuturePriorities.containsKey(highway) ? highwayFuturePriorities.get(highway) : 1d;
		return priority;
	}

	@Override
	public double defineSpeed(RouteDataObject road) {
		if (followSpeedLimitations) {
			RouteRegion reg = road.region;
			int sz = road.types.length;
			for (int i = 0; i < sz; i++) {
				RouteTypeRule r = reg.quickGetEncodingRule(road.types[i]);
				float maxSpeed = r.maxSpeed();
				if (maxSpeed > 0) {
					return maxSpeed;
				}
			}
		}

		Double value = highwaySpeed.get(road.getHighway());
		if (value == null) {
			value = minDefaultSpeed;
		}
		return value / 3.6d;
	}

	@Override
	public double defineSpeedPriority(RouteDataObject road) {
		String highway = road.getHighway();
		double priority = highway != null && highwayPriorities.containsKey(highway) ? highwayPriorities.get(highway) : 1d;
		return priority;
	}

	@Override
	public double getMinDefaultSpeed() {
		return minDefaultSpeed / 3.6d;
	}

	@Override
	public double getMaxDefaultSpeed() {
		return maxDefaultSpeed / 3.6d;
	}

	
	@Override
	public double calculateTurnTime(RouteSegment segment, RouteSegment next, int segmentEnd) {
		if (leftTurn > 0 || rightTurn > 0) {
			if (next.road.getPointsLength() > 1) {
				double a1 = segment.directionRoute(segmentEnd, segment.segmentStart > segmentEnd);
				double a2 = next.directionRoute(next.segmentStart, next.segmentStart < next.getRoad().getPointsLength() - 1);
				double diff = Math.abs(MapUtils.alignAngleDifference(a1 - a2));
				// more like UT
				if (diff < Math.PI / 4) {
					return leftTurn;
				} else if (diff < 2 * Math.PI / 3 && diff <= 3 * Math.PI / 4) {
					return rightTurn;
				}
			}
			return 0;
		}
		return 0;
	}
	

}
