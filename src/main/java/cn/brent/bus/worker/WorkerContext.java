package cn.brent.bus.worker;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

/**
 * 服务提供者上下文管理
 */
public class WorkerContext {

	private ConcurrentLinkedQueue<BusWork> workers=new ConcurrentLinkedQueue<BusWork>();
	
	private String[] brokers = {"127.0.0.1:15555"};  
	private Context ctx = null;
	private String registerToken;
	
	public WorkerContext(Context ctx,String[] brokers,String registerToken){
		this.ctx=ctx;
		this.brokers=brokers;
		this.registerToken=registerToken;
		if(this.ctx==null){
			ctx = ZMQ.context(1);
		}
	}
	
	public WorkerContext(Context ctx){
		this.ctx=ctx;
		if(this.ctx==null){
			ctx = ZMQ.context(1);
		}
	}
	
	/**
	 * 注册服务
	 * @param worker
	 */
	public void registerWorker(WorkHandler worker,int threadCount){
		for(int i=0; i<brokers.length; i++){
			String host = brokers[i].split(":")[0];
			int port = Integer.valueOf(brokers[i].split(":")[1]);
			for(int j=0; j<threadCount; j++){ 
				BusWork bus=new BusWork(ctx,host,port, registerToken, worker);
				bus.start();
				workers.add(bus);
			}
		}
	}
	
	
	/**
	 * 注销服务
	 * @param worker
	 */
	public void unRegisterWorker(String serviceName){
		try{
			Iterator<BusWork> inter=workers.iterator();
			while(inter.hasNext()){
				BusWork work=inter.next();
				if(work.getName().equals(serviceName)){
					work.stop();
					inter.remove();
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * 停止所有服务
	 */
	public void stopAll(){
		Iterator<BusWork> entryIterator=workers.iterator();
		while(entryIterator.hasNext()){
			BusWork worker=entryIterator.next();
			worker.stop();
			entryIterator.remove();
		}
	}
	
}