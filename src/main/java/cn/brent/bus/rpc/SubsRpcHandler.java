package cn.brent.bus.rpc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMsg;

import cn.brent.bus.BusException;
import cn.brent.bus.worker.WorkHandler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

public class SubsRpcHandler implements WorkHandler {

	private static final Logger logger = LoggerFactory.getLogger(SubsRpcHandler.class);
	
	private String serviceName;
	private String regToken;
	private SubsMsgHandler handler;
	
	private Set<String> topics=new HashSet<String>();
	
	public SubsRpcHandler(String serviceName,SubsMsgHandler handler) {
		this(serviceName, null,handler);
	}
	
	public SubsRpcHandler(String serviceName,String regToken,SubsMsgHandler handler) {
		this.serviceName=serviceName;
		this.regToken=regToken;
		if(handler==null){
			throw new RuntimeException("handler can't be null");
		}
		this.handler=handler;
	}
	
	/**
	 * 增加主题
	 * @param topic
	 */
	public SubsRpcHandler addTopic(String topic){
		topics.add(topic);
		return this;
	}


	@Override
	public ZMsg handleRequest(ZMsg request) {
		String text=request.popString();
		System.out.println(text);
		JSONObject res;
		try {
			res = (JSONObject) JSON.parse(text);
			handler.handle(res);
		} catch (Exception e) {
			handler.handle(text);
		}
		return null;
	}

	@Override
	public String getServiceName() {
		return serviceName;
	}

	@Override
	public Set<String> getTopics() {
		return topics;
	}

	@Override
	public String getRegToken() {
		return regToken;
	}

	@Override
	public Mode getMode() {
		return Mode.MODE_PUBSUB;
	}

}
