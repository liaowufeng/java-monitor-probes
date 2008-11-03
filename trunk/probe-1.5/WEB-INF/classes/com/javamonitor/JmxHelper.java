package com.javamonitor;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.javamonitor.mbeans.GarbageCollector;
import com.javamonitor.mbeans.Server;
import com.javamonitor.mbeans.Threading;

/**
 * The JMX facade, making JMX easy. Apart from making the JMX interface easier
 * to use, this helper also does its best to find mbeans and cache their
 * lookups. The reason for doing this is that there may be more than one mbean
 * server and we'd like to give Java-monitor a single view over all of those.
 * <p>
 * If we have to register mbeans
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class JmxHelper {
    /**
     * The base of all the helper object names.
     */
    public static final String objectNameBase = "com.javamonitor:type=";

    private static final Map<ObjectName, MBeanServer> knownMBeanServers = new HashMap<ObjectName, MBeanServer>();

    private static MBeanServer jbossMbeanServer = null;
    static {
        try {
            jbossMbeanServer = (MBeanServer) Class.forName(
                    "org.jboss.mx.util.MBeanServerLocator").getMethod(
                    "locateJBoss", (Class[]) null)
                    .invoke(null, (Object[]) null);
        } catch (Exception e) {
            // woops: we're not running in JBoss
        }
    }

    @SuppressWarnings("unchecked")
    private static MBeanServer findMBeanServer(final ObjectName objectName) {
        // any cached instance lookups?
        if (knownMBeanServers.containsKey(objectName)) {
            return knownMBeanServers.get(objectName);
        }

        // no cached instances, search high and low...
        MBeanServer mbeanServer = null;
        if (jbossMbeanServer != null) {
            try {
                if (jbossMbeanServer.getObjectInstance(objectName) != null) {
                    mbeanServer = jbossMbeanServer;
                }
            } catch (InstanceNotFoundException e) {
                // woops, not registered here...
            }
        }

        final List<MBeanServer> servers = MBeanServerFactory
                .findMBeanServer(null);
        for (int i = 0; mbeanServer == null && i < servers.size(); i++) {
            try {
                if (servers.get(i).getObjectInstance(objectName) != null) {
                    mbeanServer = servers.get(i);
                }
            } catch (InstanceNotFoundException e) {
                // woops, not registered here...
            }
        }

        if (mbeanServer == null) {
            // oh well, most likely it is here then...
            mbeanServer = ManagementFactory.getPlatformMBeanServer();
        }

        knownMBeanServers.put(objectName, mbeanServer);
        return mbeanServer;
    }

    /**
     * Check that an mbean exists.
     * 
     * @param objectName
     *            The bean to check for.
     * @return <code>true</code> if the bean exists, or <code>false</code>
     *         otherwise.
     */
    public static boolean mbeanExists(final String objectName) {
        try {
            final ObjectName name = new ObjectName(objectName);
            return findMBeanServer(name).isRegistered(name);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Register a new mbean in the platform mbean server.
     * 
     * @param mbean
     *            The mbean to register.
     * @param objectNameString
     *            The object name to register it under.
     * @throws Exception
     *             When we could not register the mbean.
     */
    private static void register(final Object mbean,
            final String objectNameString) throws Exception {
        unregister(objectNameString);

        ManagementFactory.getPlatformMBeanServer().registerMBean(mbean,
                new ObjectName(objectNameString));
    }

    /**
     * Query for a string, based on the object name as string. Convenience
     * method that does the casts.
     * 
     * @param objectName
     *            The object name to query.
     * @param attribute
     *            The attribute to query.
     * @return The value of the attribute, as string.
     * @throws Exception
     *             When there was a problem querying.
     */
    public static String queryString(final String objectName,
            final String attribute) throws Exception {
        final Object value = query(objectName, attribute);
        return value == null ? null : value.toString();
    }

    /**
     * Query for a string, based on the object name. Convenience method that
     * does the casts.
     * 
     * @param objectName
     *            The object name to query.
     * @param attribute
     *            The attribute to query.
     * @return The value of the attribute, as string.
     * @throws Exception
     *             When there was a problem querying.
     */
    public static String queryString(final ObjectName objectName,
            final String attribute) throws Exception {
        final Object value = query(objectName, attribute);
        return value == null ? null : value.toString();
    }

    /**
     * Query for an integer, based on the object name. Convenience method that
     * does the casts.
     * 
     * @param objectName
     *            The object name to query.
     * @param attribute
     *            The attribute to query.
     * @return The value of the attribute, as string.
     * @throws Exception
     *             When there was a problem querying.
     */
    public static Integer queryInt(final ObjectName objectName,
            final String attribute) throws Exception {
        final Object value = query(objectName, attribute);
        return value == null ? null : Integer.parseInt(value.toString());
    }

    /**
     * Query for a long, based on the object name. Convenience method that does
     * the casts.
     * 
     * @param objectName
     *            The object name to query.
     * @param attribute
     *            The attribute to query.
     * @return The value of the attribute, as string.
     * @throws Exception
     *             When there was a problem querying.
     */
    public static Long queryLong(final ObjectName objectName,
            final String attribute) throws Exception {
        final Object value = query(objectName, attribute);
        return value == null ? null : Long.parseLong(value.toString());
    }

    /**
     * Query for a value, based on the object name as string. Convenience method
     * that does the casts.
     * 
     * @param objectName
     *            The object name to query.
     * @param attribute
     *            The attribute to query.
     * @return The value of the attribute, as string.
     * @throws Exception
     *             When there was a problem querying.
     */
    public static Object query(final String objectName, final String attribute)
            throws Exception {
        return query(new ObjectName(objectName), attribute);
    }

    /**
     * Query a JMX attribute.
     * 
     * @param objectName
     *            The name of the mbean to query.
     * @param attribute
     *            The attribute to query on that mbean.
     * @return The value of the attribute on the named object.
     * @throws Exception
     *             When there was a problem querying.
     */
    public static Object query(final ObjectName objectName,
            final String attribute) throws Exception {
        final int dot = attribute.indexOf('.');
        if (dot < 0) {
            return findMBeanServer(objectName).getAttribute(objectName,
                    attribute);
        }

        return resolveFields((CompositeData) findMBeanServer(objectName)
                .getAttribute(objectName, attribute.substring(0, dot)),
                attribute.substring(dot + 1));
    }

    private static Object resolveFields(final CompositeData attribute,
            final String field) {
        final int dot = field.indexOf('.');
        if (dot < 0) {
            final Object ret = attribute.get(field);
            return ret == null ? null : ret;
        }

        return resolveFields((CompositeData) attribute.get(field.substring(0,
                dot)), field.substring(dot + 1));
    }

    /**
     * Find a list of object names.
     * 
     * @param query
     *            The wildcarded object name to list.
     * @return A list of matching object names.
     * @throws MalformedObjectNameException
     *             When the query could not be parsed.
     */
    @SuppressWarnings("unchecked")
    public static Set<ObjectName> queryNames(final String query)
            throws MalformedObjectNameException {
        final ObjectName objectNameQuery = new ObjectName(query);
        Set<ObjectName> names = new HashSet<ObjectName>();
        if (jbossMbeanServer != null) {
            names = jbossMbeanServer.queryNames(objectNameQuery, null);
        }

        final List<MBeanServer> servers = MBeanServerFactory
                .findMBeanServer(null);
        for (int i = 0; names.size() == 0 && i < servers.size(); i++) {
            names = servers.get(i).queryNames(objectNameQuery, null);
        }

        if (names.size() == 0) {
            names = ManagementFactory.getPlatformMBeanServer().queryMBeans(
                    objectNameQuery, null);
        }

        return names;
    }

    private static final GarbageCollector quickGc = new GarbageCollector(true);

    private static final GarbageCollector thoroughGc = new GarbageCollector(
            false);

    /**
     * Register the cool beans we need to find our way in the JMX jungle.
     * 
     * @throws Exception
     *             When we could not register one or more beans.
     */
    public static void registerCoolBeans() throws Exception {
        register(new Server(), Server.objectName);
        register(quickGc, quickGc.getObjectName());
        register(thoroughGc, thoroughGc.getObjectName());
        register(new Threading(), Threading.objectName);
    }

    /**
     * Unregister all the useful mbeans from the JMX registry. We assume that
     * the registered bean was registered in the platform mbean server.
     */
    public static void unregisterCoolBeans() {
        unregister(Server.objectName);
        unregister(quickGc.getObjectName());
        unregister(thoroughGc.getObjectName());
        unregister(Threading.objectName);
    }

    private static void unregister(final String objectName) {
        try {
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(
                    new ObjectName(objectName));
        } catch (Exception e) {
            // ignore, this was just to clean up
        }
    }
}