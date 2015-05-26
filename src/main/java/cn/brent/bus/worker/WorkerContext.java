package cn.brent.bus.worker;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.zeromq.ZMQ.Context;

import cn.brent.bus.Protocol;

/**
 * 服务提供者上下文管理
 */
public class WorkerContext {

	private ConcurrentHashMap<String,BusWork> workerMap=new ConcurrentHashMap<String,BusWork>();
	private ReentrantLock lock=new ReentrantLock();
	private CountDownLatch count=new CountDownLatch(1);
	private boolean working=true;
	
	private String host = "127.0.0.1";
	private int port = 15555;
	private Context ctx = null;
	private String registerToken;
	
	public WorkerContext(Context ctx,String host,int port,String registerToken){
		this.ctx=ctx;
		this.host=host;
		this.port=port;
		this.registerToken=registerToken;
		init();
	}
	
	public WorkerContext(Context ctx){
		this.ctx=ctx;
		init();
	}
	
	/**
	 * 初始化
	 */
	private void init(){
		new Thread(){
			public void run(){
				while(working){
					heartbeatHandler();
					try {
						count.await(Protocol.HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
					}
				}
			}
		}.start();
	}
	
	/**
	 * 发送心跳
	 */
	protected void heartbeatHandler(){
		Iterator<Entry<String,BusWork>> entryIterator=workerMap.entrySet().iterator();
		while(entryIterator.hasNext()){
			Entry<String,BusWork> entry=entryIterator.next();
			BusWork worker=entry.getValue();
			long now=System.currentTimeMillis();
			if(now>worker.getHeartbeatAt()){
				try{
					worker.sendHeartbeat();
				}catch(Exception e){
					e.printStackTrace();
					try{
						lock.lock();
						entryIterator.remove();
					}catch(Exception ee){
						ee.printStackTrace();
					}finally{
						lock.unlock();
					}
				}
			}
		}
	}
	
	/**
	 * 注册服务
	 * @param worker
	 */
	public void registerWorker(WorkHandler worker){
		BusWork bus=new BusWork(ctx,host,port, registerToken, worker);
		workerMap.put(bus.getName(),bus);
		bus.start();
	}
	
	
	/**
	 * 注销服务
	 * @param worker
	 */
	public void unRegisterWorker(String serviceName){
		try{
			lock.lock();
			BusWork work=workerMap.get(serviceName);
			if(work!=null){
				work.stop();
			}
			workerMap.remove(serviceName);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			lock.unlock();
		}
	}
	
	/**
	 * 停止所有服务
	 */
	public void stopAll(){
		working=false;
		count.countDown();
		Iterator<BusWork> entryIterator=workerMap.values().iterator();
		while(entryIterator.hasNext()){
			BusWork worker=entryIterator.next();
			entryIterator.remove();
			worker.stop();
		}
	}
	
}