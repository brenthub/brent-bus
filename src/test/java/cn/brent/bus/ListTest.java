package cn.brent.bus;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ListTest {
	
	private static Map<Long, String> services=new ConcurrentHashMap<Long, String>();

	public static void main(String[] args) throws InterruptedException {
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(true){
					Iterator<Long> iter=services.keySet().iterator();
					while(iter.hasNext()){
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						long a=iter.next();
						if(a%2==0){
							iter.remove();
						}
						System.out.println(services.size());
					}
				}
			}
		}).start();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(true){
					services.put(System.currentTimeMillis(), "0");
					if(System.currentTimeMillis()%3==0){
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}).start();
		
		Thread.sleep(100000);
	}
}
