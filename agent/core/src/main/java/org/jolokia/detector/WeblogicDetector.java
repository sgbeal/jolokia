/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.detector;

import java.util.Set;

import javax.management.MBeanServer;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Detector for Weblogic Appservers
 *
 * @author roland
 * @since 05.12.10
 */
public class WeblogicDetector extends AbstractServerDetector {

    /** {@inheritDoc} */
    public ServerHandle detect(Set<MBeanServer> pMbeanServers) {
        String domainConfigMBean = getSingleStringAttribute(pMbeanServers,"*:Name=RuntimeService,*","DomainConfiguration");
        if (domainConfigMBean != null) {
            String version = getSingleStringAttribute(pMbeanServers,domainConfigMBean,"ConfigurationVersion");
            return new ServerHandle("Bea","weblogic",version,null,null);
        }
        return null;
    }

    /**
     * Adde Weblogic specific runtime MBeanServer
     * @param servers set to add own MBean servers
     */
    @Override
    public void addMBeanServers(Set<MBeanServer> servers) {
        // Weblogic stores the MBeanServer in a JNDI context
        InitialContext ctx;
        try {
            ctx = new InitialContext();
            MBeanServer server = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");
            if (server != null) {
                servers.add(server);
            }
        } catch (NamingException e) {
            // expected and can happen on non-Weblogic platforms
        }
    }
}
