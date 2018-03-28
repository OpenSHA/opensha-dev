package scratch.aftershockStatistics;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.Format;
import gov.usgs.earthquake.event.JsonEvent;
import scratch.aftershockStatistics.cmu.EqkEventIdComparator;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.FileNotFoundException;

public class ComcatAccessor {
	
	private static final boolean D = true;
	
	private EventWebService service;
	
	public ComcatAccessor() {
		try {
			service = new EventWebService(new URL("https://earthquake.usgs.gov/fdsnws/event/1/"));
		} catch (MalformedURLException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
	}
	
	/**
	 * Fetches an event with the given ID, e.g. "ci37166079"
	 * @param eventID
	 * @return
	 * Note: The longitude is coerced to lie between -90 and +270.
	 * This is so it is possible to draw a region surrounding the mainshock,
	 * and have the entire region lie in the valid range -180 <= lon <= +360.
	 * Note: If in the future, the Region class is replaced by something that
	 * does spherical geometry, then the coercion here and below could be removed,
	 * and all longitudes could be between -180 and 180.
	 */
	public ObsEqkRupture fetchEvent(String eventID) {
		EventQuery query = new EventQuery();
		query.setEventId(eventID);
		List<JsonEvent> events;
		try {
			events = service.getEvents(query);
		} catch (FileNotFoundException e) {
			// If ComCat does not recognize the eventID, ComCat returns HTTP error 404, which appears here as FileNotFoundException.
			return null;
		} catch (IOException e) {
			// If the eventID has been deleted from ComCat, ComCat returns HTTP error 409, which appears here as IOException.
			return null;
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		if (events.isEmpty())
			return null;
		Preconditions.checkState(events.size() == 1, "More that 1 match? "+events.size());
		
		JsonEvent event = events.get(0);
//		printJSON(event);
		
		return eventToObsRup(event);
	}
	
	public static void printJSON(JSONObject json) {
		printJSON(json, "");
	}
	private static void printJSON(JSONObject json, String prefix) {
		for (Object key : json.keySet()) {
			Object val = json.get(key);
			if (val != null && val.toString().startsWith("[{")) {
				String str = val.toString();
				try {
					val = new JSONParser().parse(str.substring(1, str.length()-1));
				} catch (ParseException e) {
//					e.printStackTrace();
				}
			}
			if (val != null && val instanceof JSONObject) {
				System.out.println(prefix+key+":");
				String prefix2 = prefix;
				if (prefix2 == null)
					prefix2 = "";
				prefix2 += "\t";
				printJSON((JSONObject)val, prefix2);
			} else {
				System.out.println(prefix+key+": "+val);
			}
		}
	}
	
	static final double day_millis = 24d*60d*60d*1000d;
	
	/**
	 * Fetch all aftershocks of the given event. Returned list will not contain the mainshock
	 * even if it matches the query.
	 * @param mainshock
	 * @param minDays
	 * @param maxDays
	 * @param minDepth
	 * @param maxDepth
	 * @param region
	 * @return
	 * Note: The mainshock parameter must be a return value from fetchEvent() above.
	 * Note: If the mainshock longitude is between -90 and +90, then the aftershock
	 * longitudes lie between -180 and +180.  If the mainshock longitude is between
	 * +90 and +270, then the aftershock longitudes are coerced to lie between
	 * 0 and +360.  This makes is possible to easily test if an aftershock lies
	 * within a region surrounding the mainshock.
	 */
	public ObsEqkRupList fetchAftershocks(ObsEqkRupture mainshock, double minDays, double maxDays,
			double minDepth, double maxDepth, Region region) {
		EventQuery query = new EventQuery();
		
		Preconditions.checkState(minDepth < maxDepth, "Min depth must be less than max depth");
		query.setMinDepth(new BigDecimal(String.format("%.3f", minDepth)));
		query.setMaxDepth(new BigDecimal(String.format("%.3f", maxDepth)));
		
		Preconditions.checkState(minDays < maxDays, "Min days must be less than max days");
		// time zones shouldn't be an issue since we're just adding to the original catalog time, whatever
		// time zone that is in.
		long eventTime = mainshock.getOriginTime();
		long startTime = eventTime + (long)(minDays*day_millis);
		long endTime = eventTime + (long)(maxDays*day_millis);
		query.setStartTime(new Date(startTime));
		if(endTime==startTime)
			endTime=Instant.now().toEpochMilli();
		query.setEndTime(new Date(endTime));
		
		Preconditions.checkState(startTime < System.currentTimeMillis(), "Aftershock fetch start time is after now!");
		
		// need to set this threshold at 90 (not 180) so that mainshocks located just
		// west of the date line are handled correctly
		boolean mainshockLonWrapped = mainshock.getHypocenterLocation().getLongitude() > 90;
		
		query.setMinLatitude(new BigDecimal(String.format("%.5f", region.getMinLat())));
		query.setMaxLatitude(new BigDecimal(String.format("%.5f", region.getMaxLat())));
		query.setMinLongitude(new BigDecimal(String.format("%.5f", region.getMinLon())));
		query.setMaxLongitude(new BigDecimal(String.format("%.5f", region.getMaxLon())));
		query.setLimit(20000);
		List<JsonEvent> events;
		int count=20000;
		ObsEqkRupList rups = new ObsEqkRupList();
		Date latest=new Date(endTime);
		Date endTimeStamp;
		do{
			endTimeStamp=latest;
			query.setEndTime(latest);
			if (D)
				try {
					System.out.println(service.getUrl(query, Format.GEOJSON));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}

			try {
				events = service.getEvents(query);
				count = events.size();
			} catch (FileNotFoundException e) {
				events = null;
				count = 0;
			} catch (IOException e) {
				events = null;
				count = 0;
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			System.out.println(count);

			if (count > 0) {
				for (JsonEvent event : events) {
					boolean wrap = mainshockLonWrapped && event.getLongitude().doubleValue() < 0;
					ObsEqkRupture rup = eventToObsRup(event, wrap);
					if (rup !=null)
						rups.add(rup);
				}
			}
			rups.sortByOriginTime();
			if(count==0)
				break;
			latest=rups.get(0).getOriginTimeCal().getTime();
		}while(count==20000 && endTimeStamp.compareTo(latest)!=0);
		Collections.sort(rups, new EqkEventIdComparator());
		ObsEqkRupList delrups=new ObsEqkRupList();
		ObsEqkRupture previous =null;
		for (ObsEqkRupture rup : rups) {
			if (rup.getEventId().equals(mainshock.getEventId()) || (previous!=null && rup.getEventId().equals(previous.getEventId()))) {
				//if (D) System.out.println("Removing mainshock (M="+rup.getMag()+") from aftershock list");
				delrups.add(rup);
			}
		}
		rups.removeAll(delrups);
		if (!region.isRectangular()) {
			if (D) System.out.println("Fetched "+rups.size()+" events before region filtering");
			for (int i=rups.size(); --i>=0;)
				if (!region.contains(rups.get(i).getHypocenterLocation()))
					rups.remove(i);
		}
		
		if (D) System.out.println("Returning "+rups.size()+" aftershocks");
		
		return rups;
	}

	// This function should be private.  It is public only to avoid breaking class
	// scratch.kevin.ucerf3.LaHabraProbCalc.  New code should NOT call this function.
	public static ObsEqkRupture eventToObsRup(JsonEvent event) {
		// default to moving anything with lon < -90 to the positive domain
		// then we'll apply this consistently to all aftershocks
		// without this fix (and corresponding check in fetchEvent), events such as usp000fuse will fail
		return eventToObsRup(event, event.getLongitude().doubleValue() < -90);
	}
	
	private static ObsEqkRupture eventToObsRup(JsonEvent event, boolean wrapLon) {
		double lat = event.getLatitude().doubleValue();
		double lon = event.getLongitude().doubleValue();
		GeoTools.validateLon(lon);
		if (wrapLon) {
			lon += 360;
			GeoTools.validateLon(lon);
		}
		double dep = event.getDepth().doubleValue();
		if (dep < 0) {
			// some regional networks can report negative depths, but the definition of what they're relative to can vary between
			// networks (see http://earthquake.usgs.gov/data/comcat/data-eventterms.php#depth) so we decided to just discard any
			// negative depths rather than try to correct with a DEM (which may be inconsistant with the networks). More discussion
			// in e-mail thread 2/8-9/17 entitled "ComCat depths in OAF app"
			dep = 0;
		}
		Location hypo = new Location(lat, lon, dep);
		double mag=0;
		try{
			mag = event.getMag().doubleValue();
		}catch(Exception e){
			System.out.println(event.toString());
			return null;
		}
		ObsEqkRupture rup = new ObsEqkRupture(event.getEventId().toString(),
				event.getTime().getTime(), hypo, mag);
		
		return rup;
	}

}
