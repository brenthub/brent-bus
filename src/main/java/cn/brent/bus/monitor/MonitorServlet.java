package cn.brent.bus.monitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMsg;

import com.alibaba.fastjson.JSONObject;

import cn.brent.bus.client.BusClient;
import cn.brent.bus.client.ClientPool;
import cn.brent.bus.client.ClientPool.ClientPoolConfig;

public class MonitorServlet extends HttpServlet {
	
	private Map<String, String> resourceMap=new ConcurrentHashMap<String, String>();
	
	private final ClientPool pool;
	
	public MonitorServlet(int serverPort) {
		Context ctx = ZMQ.context(1);
		ClientPoolConfig config=new ClientPoolConfig();
		config.setMaxTotal(10);
		pool=new ClientPool(ctx, "localhost", serverPort, 10000, config);
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		String cmd = req.getParameter("cmd");
		if (StringUtils.isEmpty(cmd)) {
			indexHandler(req, response);
			return;
		} else {
			if (cmd.equals("resource")) {
				ResourceHandler(req, response);
				return;
			}
			JsonHandler(cmd, req, response);
		}
	}

	private void ResourceHandler(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException{
		String name=req.getParameter("name");
		if(StringUtils.isEmpty(name)){
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		response.setContentType("application/javascript;charset=utf-8");
		response.getWriter().write(loadFileContent(name));
	}

	private void JsonHandler(String cmd,HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json;charset=utf-8");
		BusClient client = pool.borrowClient();
		JSONObject obj=new JSONObject();
		try {
			String result="";
			if (cmd.equals("srvls")) {
				ZMsg msg=client.monitor("srvls");
				result=msg.popString();
			}else if(cmd.equals("workls")){
				String name=req.getParameter("name");
				if(StringUtils.isEmpty(name)){
					throw new RuntimeException("name is null");
				}
				ZMsg msg=new ZMsg();
				msg.add(name);
				ZMsg m=client.monitor("","workls",msg);
				result=m.popString();
			}else if(cmd.equals("clear")){
				String name=req.getParameter("name");
				if(StringUtils.isEmpty(name)){
					throw new RuntimeException("name is null");
				}
				ZMsg msg=new ZMsg();
				msg.add(name);
				ZMsg m=client.monitor("","clear",msg);
				result=m.popString();
			}else if(cmd.equals("del")){
				String name=req.getParameter("name");
				if(StringUtils.isEmpty(name)){
					throw new RuntimeException("name is null");
				}
				ZMsg msg=new ZMsg();
				msg.add(name);
				ZMsg m=client.monitor("","del",msg);
				result=m.popString();
			}else{
				throw new RuntimeException("unkown cmd");
			}
			Object data=null;
			try {
				data=JSONObject.parse(result);
			} catch (Exception e) {
				data=result;
			}
			obj.put("state", true);
			obj.put("data", data);
		} catch (Exception e) {
			e.printStackTrace();
			obj.put("state", false);
			obj.put("msg", e.getMessage());
		}
		response.getWriter().write(obj.toString());
		response.setStatus(HttpServletResponse.SC_OK);
		
	}

	private void indexHandler(HttpServletRequest req, HttpServletResponse response) throws ServletException,
			IOException {
		response.setContentType("text/html;charset=utf-8");
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().write(loadFileContent("monitor.htm"));
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doGet(req, resp);
	}

	
	protected String loadFileContent(String resource) {
		String body=resourceMap.get(resource);
		if(body!=null){
			return body;
		}
		InputStream in = MonitorServlet.class.getClassLoader().getResourceAsStream("bus-monitor/"+resource);
		if (in == null)
			return "";

		Writer writer = new StringWriter();
		char[] buffer = new char[1024];
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			int n;
			while ((n = reader.read(buffer)) != -1) {
				writer.write(buffer, 0, n);
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
//		resourceMap.put(resource, writer.toString());
		return writer.toString();
	}
}
