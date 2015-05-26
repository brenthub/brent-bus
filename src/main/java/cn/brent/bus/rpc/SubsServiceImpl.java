package cn.brent.bus.rpc;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMsg;

import cn.brent.bus.client.BusClient;
import cn.brent.bus.client.ClientPool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

public class SubsServiceImpl implements SubsService {
	
	Logger logger =LoggerFactory.getLogger(SubsServiceImpl.class);

	private  ClientPool pool;
	private String serviceName;
	private String topic;
	private String accessToken = "";
	private Integer timeout; 
	private boolean log = true;
	
	public SubsServiceImpl(Map<String, String> kvs, ClientPool pool) {
		this.pool=pool;
		this.serviceName=kvs.get("serviceName");
		this.topic=kvs.get("topic");
		this.accessToken=kvs.get("accessToken");
		if(StringUtils.isNotEmpty(kvs.get("timeout"))){
			this.timeout=Integer.parseInt(kvs.get("timeout"));
		}
	}

	@Override
	public boolean publish(Object obj) {
		boolean ret=true;
		ZMsg message = new ZMsg();
		message.add(JSON.toJSONBytes(obj,SerializerFeature.WriteMapNullValue,SerializerFeature.WriteClassName));
		BusClient client=null;
		try{
			client = pool.borrowClient();
			if(timeout!=null){
				client.setTimeout(timeout);
			}
			long start=System.currentTimeMillis();
			ret=client.publish(serviceName, accessToken,topic, message);
			if(log){
				logger.info("publish\t"+obj+"\t"+ret+"\t"+(System.currentTimeMillis()-start));
			}
		}catch(Exception e){
			e.printStackTrace();
			logger.error("",e);
			ret=false;
		}finally{
			if(client!=null){
				pool.returnClient(client);
			}
		}
		return ret;
	}

	@Override
	public boolean publish(String topic, Object obj) {
		return publish(topic,obj);
	}

}
