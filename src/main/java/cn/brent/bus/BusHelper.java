package cn.brent.bus;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BusHelper {

	public static String getLocalIp() {
		try {
			Pattern pattern = Pattern.compile("(192|172|10)\\.[0-9]+\\.[0-9]+\\.[0-9]+");
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

			while (interfaces.hasMoreElements()) {
				NetworkInterface ni = interfaces.nextElement();
				Enumeration<InetAddress> en = ni.getInetAddresses();
				while (en.hasMoreElements()) {
					InetAddress addr = en.nextElement();
					String ip = addr.getHostAddress();
					Matcher matcher = pattern.matcher(ip);
					if (matcher.matches()) {
						return ip;
					}
				}
			}
			return "0.0.0.0";
		} catch (Throwable e) {
			e.printStackTrace();
			return "0.0.0.0";
		}
	}
}
