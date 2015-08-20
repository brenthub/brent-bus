package cn.brent.bus.monitor;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class MonitorServer {
	
	private int monitorPort=15556;
	private int serverPort=15555;
	
	public MonitorServer(int serverPort,int monitorPort) {
		this.monitorPort=monitorPort;
		this.serverPort=serverPort;
	}
	
	public MonitorServer() {
	}
	
	public void start() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				startJetty();
			}
		}).start();
		
	}
	
	protected void startJetty(){
		try {
			Server server = new Server(monitorPort);
			ServletContextHandler sch=new ServletContextHandler();
			sch.addServlet(new ServletHolder(new MonitorServlet(this.serverPort)), "/");
			server.setHandler(sch);
			server.start();
			server.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
