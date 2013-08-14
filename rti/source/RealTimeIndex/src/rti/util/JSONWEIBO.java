package rti.util;

import java.io.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import rti.core.RealTimeIndex;

public class JSONWEIBO {
	
	public static String title = "statuses";	
	public static String dateLabel = "created_at";
	
	public static String[] strLabel = {"text","source","in_reply_to_status_id","in_reply_to_user_id","in_reply_to_screen_name","mid"};
	public static String[] longLabel = {"id","reposts_count","comments_count","mlevel"};
	public static String[] boolLabel = {"favorited","truncated"};
	public static String[] objLabel = {"geo","user","annotations","visible"};
	
	public static JSONArray array = null;
	
	public static JSONArray toJSONArray(String weibo) throws JSONException
	{
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(weibo), RealTimeIndex.CHARSET));
			String line = null;
			
			StringBuffer strBuff = new StringBuffer();
			
			while((line = br.readLine())!=null)
			{
				strBuff.append(line);
			}
			
			JSONObject jsonObj = new JSONObject(strBuff.toString());
			array = jsonObj.getJSONArray(title);
		}
		catch(IOException e)
		{
			e.printStackTrace(); 
		}
		
		return array;
	}
	
	public static JSONObject getJSONObject(int index) throws JSONException
	{
		JSONObject jsonObj = null;
		
		if (array.length() <= index)
		{
			return null;
		}
		
		jsonObj = array.getJSONObject(index);
		
		return jsonObj;
	}
	
	public static int getLength()
	{
		return array.length();
	}
}


