package gov.nysenate.openleg.api.servlets;

import gov.nysenate.openleg.api.AbstractApiRequest;
import gov.nysenate.openleg.api.AbstractApiRequest.ApiRequestException;
import gov.nysenate.openleg.api.KeyValueViewRequest;
import gov.nysenate.openleg.api.KeyValueViewRequest.KeyValueView;
import gov.nysenate.openleg.api.MultiViewRequest;
import gov.nysenate.openleg.api.MultiViewRequest.MultiView;
import gov.nysenate.openleg.api.SearchRequest;
import gov.nysenate.openleg.api.SingleViewRequest;
import gov.nysenate.openleg.api.SearchRequest.SearchView;
import gov.nysenate.openleg.api.SingleViewRequest.SingleView;
import gov.nysenate.openleg.util.OpenLegConstants;
import gov.nysenate.openleg.util.TextFormatter;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/*
 * a cleaner and [hopefully] more intelligent catch all for generated views
 */
@SuppressWarnings("serial")
public class ApiServlet extends HttpServlet implements OpenLegConstants {
	
	public static final int SINGLE_FORMAT = 1;
	public static final int SINGLE_TYPE = 2;
	public static final int SINGLE_ID = 3;
	
	public static final int MULTI_FORMAT = 1;
	public static final int MULTI_TYPE = 2;
	public static final int MULTI_PAGE_NUMBER = 3;
	public static final int MULTI_PAGE_SIZE = 4;
	
	public static final int KEY_VALUE_FORMAT = 1;
	public static final int KEY_VALUE_KEY = 2;
	public static final int KEY_VALUE_VALUE = 3;
	public static final int KEY_VALUE_PAGE_NUMBER = 4;
	public static final int KEY_VALUE_PAGE_SIZE = 5;
	
	/*
	 * Used to match the start of a single, multi or key value view..
	 * 		/legislation/[view type]
	 * 		/legislation/api/[view type]
	 * 		/legislation/api/1.0/[view type]
	 */
	public static final String BASE_START = "^(?i)/legislation/(?:(?:api/)(?:(?<=api/)1\\.0/)?(?:(";
	
	/*
	 * Ends base start, surrounds possible formats associated with a view
	 */
	public static final String BASE_MIDDLE = ")/))?(";
	
	public static final String BASE_END = "$";
	
	/*
	 * multi views and key value views have an optional
	 * paging mechanism.. can end with
	 * 		../[page]
	 * 		../[page]/[page size]
	 */
	public static final String PAGING = "(?:(\\d+)/?+)?(?:(\\d+)/?)?";
	
	/*
	 * Captures ID from single view
	 */
	public static final String SINGLE_END = ")/(.+)";
	
	public static final String MULTI_END = ")/?";
	
	/*
	 * Captures value for Key Value view
	 */
	public static final String KEY_VALUE_END = ")/(.*?)/?+";
	
	public final Pattern SINGLE_PATTERN;
	public final Pattern MULTI_PATTERN;
	public final Pattern KEY_VALUE_PATTERN;
	public final Pattern SEARCH_PATTERN;
	
	private final Logger logger = Logger.getLogger(ApiServlet.class);
	
	/*
	 * Generates patterns based on views listed
	 * in SingleView, MultiView and KeyValueView enums
	 */
	public ApiServlet() throws ServletException {
		super();
		
		String singleViews = new Join<SingleView>() {
			public String value(SingleView t) {
				return t.view;
			}
		}.join(SingleView.values(), "|");
		
		String singleFormats = new Join<String>() {
			public String value(String t) {
				return t;
			}
		}.join(AbstractApiRequest.getUniqueFormats(SingleView.values()), "|");
				
		String multiViews = new Join<MultiView>() {
			public String value(MultiView t) {
				return t.view;
			}
		}.join(MultiView.values(), "|");
		
		String multiFormats = new Join<String>() {
			public String value(String t) {
				return t;
			}
		}.join(AbstractApiRequest.getUniqueFormats(MultiView.values()), "|");
		
		String keyValueViews = new Join<KeyValueView>() {
			public String value(KeyValueView t) {
				return t.view;
			}
		}.join(KeyValueView.values(), "|");
		
		String keyValueFormats = new Join<String>() {
			public String value(String t) {
				return t;
			}
		}.join(AbstractApiRequest.getUniqueFormats(KeyValueView.values()), "|");
		
		String searchViews = new Join<SearchView>() {
			public String value(SearchView t) {
				return t.view;
			}
		}.join(SearchView.values(), "|");
		
		String searchFormats = new Join<String>() {
			public String value(String t) {
				return t;
			}
		}.join(AbstractApiRequest.getUniqueFormats(SearchView.values()), "|");
		
		SINGLE_PATTERN = Pattern.compile(
				TextFormatter.append(
					BASE_START,singleFormats,BASE_MIDDLE,singleViews,SINGLE_END,BASE_END)
			);
		logger.info(TextFormatter.append("Single View pattern generated: ", SINGLE_PATTERN.pattern()));
		
		MULTI_PATTERN = Pattern.compile(
				TextFormatter.append(
					BASE_START,multiFormats,BASE_MIDDLE,multiViews,MULTI_END,PAGING,BASE_END) 
			);
		logger.info(TextFormatter.append("Multi View pattern generated: ", SINGLE_PATTERN.pattern()));
				
		KEY_VALUE_PATTERN = Pattern.compile(
				TextFormatter.append(
					BASE_START,keyValueFormats,BASE_MIDDLE,keyValueViews,KEY_VALUE_END,PAGING,BASE_END)
			);
		logger.info(TextFormatter.append("Key Value View pattern generated: ", SINGLE_PATTERN.pattern()));
		
		SEARCH_PATTERN = Pattern.compile(
				TextFormatter.append(
						BASE_START,searchFormats,BASE_MIDDLE,searchViews,KEY_VALUE_END,PAGING,BASE_END)
			);
		logger.info(TextFormatter.append("Search vView pattern generated: ", SEARCH_PATTERN.pattern()));
	}
	
	
	/**
	 * Tries to match request URI to patterns generated above, if successful creates
	 * applicable ApiRequest extension and routes request
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response)	throws ServletException, IOException {
		Matcher m = null;
		
		String uri = URLDecoder.decode(request.getRequestURI(), ENCODING);
		
		AbstractApiRequest apiRequest = null;
		
		/*
		 *	/legislation/(api/(1.0/)?[format]/)?[type]/[id]
		 *
		 *		ex. /legislation/api/html/bill/s1234-2011
		 */
		if(apiRequest == null && (m = SINGLE_PATTERN.matcher(uri)) != null && m.find()) {
			logger.info(TextFormatter.append("Single request: ", uri));
			
			apiRequest = new SingleViewRequest(	request, 
												response, 
												m.group(SINGLE_FORMAT),
												m.group(SINGLE_TYPE),
												m.group(SINGLE_ID));
		}
		
		/*
		 *	/legislation/(api/(1.0/)?)?[type](/[page number](/[page size]))?
		 *
		 *		ex.  legislation/bills/1/20 (first page, 20 bills a page)
		 */
		if(apiRequest == null && (m = MULTI_PATTERN.matcher(uri)) != null && m.find()) {
			logger.info(TextFormatter.append("Multi request: ", uri));
			
			apiRequest = new MultiViewRequest(	request,
												response,
												m.group(MULTI_FORMAT),
												m.group(MULTI_TYPE),
												m.group(MULTI_PAGE_NUMBER),
												m.group(MULTI_PAGE_SIZE));
		}
		
		/*
		 *	/legislation/(api/(1.0/)?)?[key]/[value](/[page number](/[page size]))?
		 *
		 *		ex. /legislation/api/committee/finance
		 */
		if(apiRequest == null && (m = KEY_VALUE_PATTERN.matcher(uri)) != null && m.find()) {
			logger.info(TextFormatter.append("Key value request: ", uri));
			
			apiRequest = new KeyValueViewRequest(	request,
													response,
													m.group(KEY_VALUE_FORMAT),
													m.group(KEY_VALUE_KEY),
													m.group(KEY_VALUE_VALUE),
													m.group(KEY_VALUE_PAGE_NUMBER),
													m.group(KEY_VALUE_PAGE_SIZE));
		}
		
		if(apiRequest == null && (m = SEARCH_PATTERN.matcher(uri)) != null && m.find()) {
			logger.info(TextFormatter.append("Search request: ", uri));
			
			apiRequest = new SearchRequest(		request,
												response,
												m.group(KEY_VALUE_FORMAT),
												m.group(KEY_VALUE_KEY),
												m.group(KEY_VALUE_VALUE),
												m.group(KEY_VALUE_PAGE_NUMBER),
												m.group(KEY_VALUE_PAGE_SIZE));
		}
		
		try {
			if(apiRequest == null) throw new ApiRequestException(
					TextFormatter.append("Failed to route request: ", uri));
			
			apiRequest.execute();
		}
		catch(ApiRequestException e) {
			logger.error(e);
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
    }	
	
	/*
	 * Used here to easily join lists of values
	 */
	static abstract class Join<T> {
		public abstract String value(T t);
		
		public String join(Iterable<T> iterable, String on) {
			StringBuffer buf = new StringBuffer();
			
			Iterator<T> iter = iterable.iterator();
			
			if(iter.hasNext())
				buf.append(value(iter.next()));
			
			while(iter.hasNext()) {
				buf.append(on);
				buf.append(value(iter.next()));
			}
			
			return buf.toString();
		}
		
		public String join(T[] array, String on) {
			StringBuffer buf = new StringBuffer();
			int length = array.length;
			
			if(length == 0) return buf.toString();
			
			buf.append(value(array[0]));
			
			for(int i = 1; i < array.length; i++) {
				buf.append(on);
				buf.append(value(array[i]));
			}
			return buf.toString();
		}
	}
}
