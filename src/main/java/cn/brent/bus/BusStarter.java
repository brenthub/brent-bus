package cn.brent.bus;

import org.zeromq.ZMQ;

import cn.brent.bus.monitor.MonitorServer;
import cn.brent.bus.server.BusServer;
import cn.brent.bus.util.Prop;

public class BusStarter {

	public static final String CONFIG = "bus-config.properties";

	public static void main(String[] args){
		String config = null;
		if (args.length > 0) {
			config = args[0];
		}
		start(config);
	}

	public static void start(String config){
		if (config == null) {
			config = CONFIG;
		}
		Prop pro = new Prop(config);

		int port = pro.getInt("port", 15555);
		int ioThreads = pro.getInt("ioThreads", 1);
		String registerToken = pro.get("registerToken");
		boolean startMonitor = pro.getBoolean("startMonitor", true);
		int monitorPort = pro.getInt("monitorPort", 15556);

		BusServer server = new BusServer(ZMQ.context(ioThreads), port, registerToken);
		server.start();

		if (startMonitor) {
			MonitorServer ms = new MonitorServer(port, monitorPort);
			ms.start();
		}
	}

}
