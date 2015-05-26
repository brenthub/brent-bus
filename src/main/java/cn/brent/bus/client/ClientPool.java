package cn.brent.bus.client;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

import cn.brent.bus.BusException;

public class ClientPool {
	
	protected Context ctx;
	protected GenericObjectPool<BusClient> internalPool;

	public ClientPool(Context ctx,String host,int port,Integer timeout,ClientPoolConfig config) {
		if (ctx == null) {
			this.ctx = ZMQ.context(1);
		}else{
			this.ctx=ctx;
		}
		
		if(config==null){
			config = new ClientPoolConfig();
		}
		this.internalPool = new GenericObjectPool<BusClient>(new BusClientFactory(this.ctx,host,port,timeout),config);
	}
	
	public ClientPool(String host,int port,Integer timeout) {
		this(null, host, port,timeout,null);
	}
	
	public ClientPool(String host,int port) {
		this(host, port,null);
	}
	
	public ClientPool() {
		this("localhost", 15555);
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

		private Context ctx;
		private String host;
		private int port;
		private Integer timeout;
		
		public BusClientFactory(Context ctx, String host, int port) {
			super();
			this.ctx = ctx;
			this.host = host;
			this.port = port;
		}
		
		public BusClientFactory(Context ctx, String host, int port,Integer timeout) {
			super();
			this.ctx = ctx;
			this.host = host;
			this.port = port;
			this.timeout=timeout;
		}

		@Override
		public boolean validateObject(PooledObject<BusClient> client) {
			try {
				int rc = client.getObject().probe();
				if (rc != 0) {
					System.err.println("validate error");
				}
				return rc == 0;
			} catch (final Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		
		@Override
		public void destroyObject(PooledObject<BusClient> client) throws Exception {
			client.getObject().destroy();
		}

		@Override
		public BusClient create() throws Exception {
			BusClient client=new BusClient(ctx, host, port);
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


