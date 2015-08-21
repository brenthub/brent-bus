package cn.brent.bus.client;

import java.util.Random;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

import cn.brent.bus.BusException;

public class ClientPool {
	
	protected Context ctx;
	protected GenericObjectPool<BusClient> internalPool;

	public ClientPool(Context ctx,String[] brokers,Integer timeout,ClientPoolConfig config) {
		if (ctx == null) {
			this.ctx = ZMQ.context(1);
		}else{
			this.ctx=ctx;
		}
		
		if(config==null){
			config = new ClientPoolConfig();
		}
		this.internalPool = new GenericObjectPool<BusClient>(new BusClientFactory(this.ctx,brokers,timeout),config);
	}
	
	public ClientPool(String[] brokers,Integer timeout) {
		this(null, brokers,timeout,null);
	}
	
	public ClientPool(String[] brokers) {
		this(brokers,null);
	}
	
	public BusClient borrowClient() {
		try {
			return (BusClient) internalPool.borrowObject();
		} catch (Exception e) {
			throw new BusException("Could not get a resource from the pool", e);
		}
	}

	public void returnClient(final BusClient client) {
		try {
			internalPool.returnObject(client);
		} catch (Exception e) {
			throw new BusException("Could not return the resource to the pool", e);
		}
	}

	public void destroy() {
		try {
			internalPool.close();
		} catch (Exception e) {
			throw new BusException("Could not destroy the pool", e);
		} finally {
			if (this.ctx != null) {
				this.ctx.term();
			}
		}
	}
	
	public static class ClientPoolConfig extends GenericObjectPoolConfig{
		
	}

	public class BusClientFactory extends BasePooledObjectFactory<BusClient> {
		
		protected Logger logger=LoggerFactory.getLogger(getClass());

		private Context ctx;
		private String[] brokers;
		private Integer timeout;
		private Random random=new Random();
		
		public BusClientFactory(Context ctx, String[] brokers) {
			this(ctx, brokers, null);
		}
		
		public BusClientFactory(Context ctx, String[] brokers,Integer timeout) {
			super();
			this.ctx = ctx;
			this.brokers = brokers;
			this.timeout=timeout;
		}

		@Override
		public boolean validateObject(PooledObject<BusClient> client) {
			try {
				int rc = client.getObject().probe();
				if (rc != 0) {
					logger.error("validate error");
				}
				return rc == 0;
			} catch (final Exception e) {
				logger.error(e.getMessage(),e);
				return false;
			}
		}
		
		@Override
		public void destroyObject(PooledObject<BusClient> client) throws Exception {
			client.getObject().destroy();
		}

		@Override
		public BusClient create() throws Exception {
			String broker=brokers[random.nextInt(brokers.length)];
			BusClient client=new BusClient(ctx, broker);
			if(timeout!=null){
				client.setTimeout(timeout);
			}
			return client;
		}

		@Override
		public PooledObject<BusClient> wrap(BusClient obj) {
			return new DefaultPooledObject<BusClient>(obj);
		}
	}
}


