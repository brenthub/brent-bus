package cn.brent.bus;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

public class Jetty {

	@Test
	public void test() throws Exception{
		Server server = new Server(8989);
		ServletContextHandler sch=new ServletContextHandler();
		HttpServlet sv=new HttpServlet() {
			private static final long serialVersionUID = 1L;
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.getWriter().write("hello,world!");
				resp.getWriter().close();
			}
		};
		sch.addServlet(new ServletHolder(sv),"/");
		server.setHandler(sch);
		server.start();
		server.join();
	}
}
