/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.bundlerepository.impl;

import java.net.URL;
import java.util.*;

import org.apache.felix.bundlerepository.*;
import org.apache.felix.bundlerepository.impl.FileUtil;
import org.apache.felix.bundlerepository.impl.LocalRepositoryImpl;
import org.apache.felix.bundlerepository.impl.LocalResourceImpl;
import org.apache.felix.bundlerepository.impl.ReasonImpl;
import org.apache.felix.bundlerepository.impl.ResourceCapability;
import org.apache.felix.bundlerepository.impl.ResourceCapabilityImpl;
import org.apache.felix.bundlerepository.impl.ResourceImpl;
import org.apache.felix.bundlerepository.impl.SystemRepositoryImpl;
import org.apache.felix.utils.log.Logger;
import org.osgi.framework.*;

public class ResolverImpl implements Resolver
{
    private final BundleContext m_context;
    private final Logger m_logger;
    private final Repository[] m_repositories;
    private final Set<Resource> m_addedSet = new HashSet<Resource>();
    private final Set<Requirement> m_addedRequirementSet = new HashSet<Requirement>();
    private final Set<Capability> m_globalCapabilities = new HashSet<Capability>();
    private final Set<Resource> m_failedSet = new HashSet<Resource>();
    private final Set<Resource> m_resolveSet = new HashSet<Resource>();
    private final Set<Resource> m_requiredSet = new HashSet<Resource>();
    private final Set<Resource> m_optionalSet = new HashSet<Resource>();
    private final Map<Resource, List<Reason>> m_reasonMap = new HashMap<Resource, List<Reason>>();
    private final Set<Reason> m_unsatisfiedSet = new HashSet<Reason>();
    private boolean m_resolved = false;
    private long m_resolveTimeStamp;
    private int m_resolutionFlags;
//    private int m_deployFlags;

    public ResolverImpl(BundleContext context, Repository[] repositories, Logger logger)
    {
        m_context = context;
        m_logger = logger;
        m_repositories = repositories;
    }

    public synchronized void add(Resource resource)
    {
        m_resolved = false;
        m_addedSet.add(resource);
    }

    public synchronized Resource[] getAddedResources()
    {
        return m_addedSet.toArray(new Resource[m_addedSet.size()]);
    }

    public synchronized void add(Requirement requirement)
    {
        m_resolved = false;
        m_addedRequirementSet.add(requirement);
    }

    public synchronized Requirement[] getAddedRequirements()
    {
        return m_addedRequirementSet.toArray(new Requirement[m_addedRequirementSet.size()]);
    }

    public void addGlobalCapability(Capability capability)
    {
        m_globalCapabilities.add(capability);
    }

    public Capability[] getGlobalCapabilities()
    {
        return m_globalCapabilities.toArray(new Capability[m_globalCapabilities.size()]);
    }

    public synchronized Resource[] getRequiredResources()
    {
        if (m_resolved)
        {
            return m_requiredSet.toArray(new Resource[m_requiredSet.size()]);
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Resource[] getOptionalResources()
    {
        if (m_resolved)
        {
            return m_optionalSet.toArray(new Resource[m_optionalSet.size()]);
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Reason[] getReason(Resource resource)
    {
        if (m_resolved)
        {
            List<Reason> l = m_reasonMap.get(resource);
            return l != null ? (Reason[]) l.toArray(new Reason[l.size()]) : null;
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Reason[] getUnsatisfiedRequirements()
    {
        if (m_resolved)
        {
            return m_unsatisfiedSet.toArray(new Reason[m_unsatisfiedSet.size()]);
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    private Resource[] getResources(boolean local)
    {
        List<Resource> resources = new ArrayList<Resource>();
        for (int repoIdx = 0; (m_repositories != null) && (repoIdx < m_repositories.length); repoIdx++)
        {
            boolean isLocal = m_repositories[repoIdx] instanceof LocalRepositoryImpl;
            boolean isSystem = m_repositories[repoIdx] instanceof SystemRepositoryImpl;
            if (isLocal && (m_resolutionFlags & NO_LOCAL_RESOURCES) != 0) {
                continue;
            }
            if (isSystem && (m_resolutionFlags & NO_SYSTEM_BUNDLE) != 0) {
                continue;
            }
            Resource[] res = m_repositories[repoIdx].getResources();
            for (int resIdx = 0; (res != null) && (resIdx < res.length); resIdx++)
            {
                if (res[resIdx].isLocal() == local)
                {
                    resources.add(res[resIdx]);
                }
            }
        }
        return resources.toArray(new Resource[resources.size()]);
    }

    public synchronized boolean resolve()
    {
        return resolve(0);
    }

    public synchronized boolean resolve(int flags)
    {
        // Find resources
        Resource[] locals = getResources(true);
        Resource[] remotes = getResources(false);

        // time of the resolution process start
        m_resolveTimeStamp = 0;
        for (int repoIdx = 0; (m_repositories != null) && (repoIdx < m_repositories.length); repoIdx++)
        {
            m_resolveTimeStamp = Math.max(m_resolveTimeStamp, m_repositories[repoIdx].getLastModified());
        }

        // Reset instance values.
        m_failedSet.clear();
        m_resolveSet.clear();
        m_requiredSet.clear();
        m_optionalSet.clear();
        m_reasonMap.clear();
        m_unsatisfiedSet.clear();
        m_resolved = true;
        m_resolutionFlags = flags;

        boolean result = true;

        // Add a fake resource if needed
        if (!m_addedRequirementSet.isEmpty() || !m_globalCapabilities.isEmpty())
        {
            ResourceImpl fake = new ResourceImpl();
            for (Iterator<Capability> iter = m_globalCapabilities.iterator(); iter.hasNext();)
            {
                Capability cap = iter.next();
                fake.addCapability(cap);
            }
            for (Iterator<Requirement> iter = m_addedRequirementSet.iterator(); iter.hasNext();)
            {
                Requirement req = iter.next();
                fake.addRequire(req);
            }
            if (!resolve(fake, locals, remotes, false))
            {
                result = false;
            }
        }

        // Loop through each resource in added list and resolve.
        for (Iterator<Resource> iter = m_addedSet.iterator(); iter.hasNext(); )
        {
            if (!resolve(iter.next(), locals, remotes, false))
            {
                // If any resource does not resolve, then the
                // entire result will be false.
                result = false;
            }
        }

        // Clean up the resulting data structures.
        m_requiredSet.removeAll(m_addedSet);
        if ((flags & NO_LOCAL_RESOURCES) == 0)
        {
            m_requiredSet.removeAll(Arrays.asList(locals));
        }
        m_optionalSet.removeAll(m_addedSet);
        m_optionalSet.removeAll(m_requiredSet);
        if ((flags & NO_LOCAL_RESOURCES) == 0)
        {
            m_optionalSet.removeAll(Arrays.asList(locals));
        }

        // Return final result.
        return result;
    }

    private boolean resolve(Resource resource, Resource[] locals, Resource[] remotes, boolean optional)
    {
    	System.out.println("resolving: " + resource.getId());
        boolean result = true;

        // Check for cycles.
        if (m_resolveSet.contains(resource) || m_requiredSet.contains(resource)) {
        	return true;
        } else if (m_optionalSet.contains(resource)) {
        	if (optional) {
        		// previously resolved as optional, optional now too --> nothing to do
        		return true;
        	}
			// previously resolved as optional, but now has to be resolved as required --> resolve!
			m_optionalSet.remove(resource);
			// continue with resolving
        }

        // Add to resolve map to avoid cycles.
        m_resolveSet.add(resource);

        // Resolve the requirements for the resource according to the
        // search order of: added, resolving, local and finally remote
        // resources.
        Requirement[] reqs = resource.getRequirements();
        Capability[] caps = resource.getCapabilities();
        if (reqs != null)
        {
            Resource candidate;
            for (int reqIdx = 0; reqIdx < reqs.length; reqIdx++)
            {
                // Do not resolve optional requirements
                if ((m_resolutionFlags & NO_OPTIONAL_RESOURCES) != 0 && reqs[reqIdx].isOptional())
                {
                    continue;
                }
                
                // skip resolving current requirement if it is a package import that is also exported
                if (isSubstitutableExport(reqs[reqIdx], caps))
                {
                	continue;
                }
                
                candidate = searchResources(reqs[reqIdx], m_addedSet);
                if (candidate == null)
                {
                    candidate = searchResources(reqs[reqIdx], m_requiredSet);
                }
                if (candidate == null)
                {
                    candidate = searchResources(reqs[reqIdx], m_optionalSet);
                }
                if (candidate == null)
                {
                    candidate = searchResources(reqs[reqIdx], m_resolveSet);
                }
                if (candidate == null)
                {
                    List<ResourceCapability> candidateCapabilities = searchResources(reqs[reqIdx], locals);
                    candidateCapabilities.addAll(searchResources(reqs[reqIdx], remotes));

                    // Determine the best candidate available that
                    // can resolve.
                    while ((candidate == null) && !candidateCapabilities.isEmpty())
                    {
                        ResourceCapability bestCapability = getBestCandidate(candidateCapabilities);

                        // Try to resolve the best resource.
                        if (resolve(bestCapability.getResource(), locals, remotes, optional || reqs[reqIdx].isOptional()))
                        {
                            candidate = bestCapability.getResource();
                        }
                        else
                        {
                            candidateCapabilities.remove(bestCapability);
                        }
                    }
                }

                if ((candidate == null) && !reqs[reqIdx].isOptional())
                {
                    // The resolve failed.
                    result = false;
                    // Associated the current resource to the requirement
                    // in the unsatisfied requirement set.
                    m_unsatisfiedSet.add(new ReasonImpl(resource, reqs[reqIdx]));
                }
                else if (candidate != null)
                {

                    // Try to resolve the candidate.
                    if (resolve(candidate, locals, remotes, optional || reqs[reqIdx].isOptional()))
                    {
                        // The resolved succeeded; record the candidate
                        // as either optional or required.
                        if (optional || reqs[reqIdx].isOptional())
                        {
                        	System.out.println(".adding to optional " + candidate);
                            m_optionalSet.add(candidate);
                            m_resolveSet.remove(candidate);
                        }
                        else
                        {
                        	System.out.println(".adding to required " + candidate);
                            m_requiredSet.add(candidate);
                            m_optionalSet.remove(candidate);
                            m_resolveSet.remove(candidate);
                        }
                        
                        System.out.println(">internal var status:");
                        System.out.println(">>required: " + m_requiredSet);
                        System.out.println(">>optional: " + m_optionalSet);

                        // Add the reason why the candidate was selected.
                        List<Reason> reasons = m_reasonMap.get(candidate);
                        if (reasons == null)
                        {
                            reasons = new ArrayList<Reason>();
                            m_reasonMap.put(candidate, reasons);
                        }
                        reasons.add(new ReasonImpl(resource, reqs[reqIdx]));
                    }
                    else
                    {
                        result = false;
                    }
                }
            }
        }

        // If the resolve failed, remove the resource from the resolve set and
        // add it to the failed set to avoid trying to resolve it again.
        if (!result)
        {
            m_resolveSet.remove(resource);
            m_failedSet.add(resource);
        }

        return result;
    }

    private static boolean isSubstitutableExport(Requirement req, Capability[] caps) {
    	for (Capability capability : caps) {
			if (req.isSatisfied(capability)) return true;
		}
		return false;
	}

	private static Resource searchResources(Requirement req, Set<Resource> resourceSet)
    {
        for (Iterator<Resource> iter = resourceSet.iterator(); iter.hasNext(); )
        {
            checkInterrupt();
            Resource resource = iter.next();
            Capability[] caps = resource.getCapabilities();
            for (int capIdx = 0; (caps != null) && (capIdx < caps.length); capIdx++)
            {
                if (req.isSatisfied(caps[capIdx]))
                {
                    // The requirement is already satisfied an existing
                    // resource, return the resource.
                    return resource;
                }
            }
        }

        return null;
    }

    /**
     * Searches for resources that do meet the given requirement
     * @param req the the requirement that must be satisfied by resources
     * @param resources list of resources to look at
     * @return all resources meeting the given requirement
     */
    private List<ResourceCapability> searchResources(Requirement req, Resource[] resources)
    {
        List<ResourceCapability> matchingCapabilities = new ArrayList<ResourceCapability>();

        for (int resIdx = 0; (resources != null) && (resIdx < resources.length); resIdx++)
        {
            checkInterrupt();
            // We don't need to look at resources we've already looked at.
            if (!m_failedSet.contains(resources[resIdx]))
            {
                Capability[] caps = resources[resIdx].getCapabilities();
                for (int capIdx = 0; (caps != null) && (capIdx < caps.length); capIdx++)
                {
                    if (req.isSatisfied(caps[capIdx]))
                    {
                        matchingCapabilities.add(new ResourceCapabilityImpl(resources[resIdx], caps[capIdx]));
                    }
                }
            }
        }

        return matchingCapabilities;
    }

    /**
     * Determines which resource is preferred to deliver the required capability.
     * This method selects the resource providing the highest version of the capability.
     * If two resources provide the same version of the capability, the resource with
     * the largest number of cabailities be preferred
     * @param caps
     * @return
     */
    private ResourceCapability getBestCandidate(List<ResourceCapability> caps)
    {
        Version bestVersion = null;
        ResourceCapability best = null;
        boolean bestLocal = false;

        for(int capIdx = 0; capIdx < caps.size(); capIdx++)
        {
            ResourceCapability current = caps.get(capIdx);
            boolean isCurrentLocal = current.getResource().isLocal();

            if (best == null)
            {
                best = current;
                bestLocal = isCurrentLocal;
                Object v = current.getCapability().getPropertiesAsMap().get(Resource.VERSION);
                if ((v != null) && (v instanceof Version))
                {
                    bestVersion = (Version) v;
                }
            }
            else if ((m_resolutionFlags & DO_NOT_PREFER_LOCAL) != 0 || !bestLocal || isCurrentLocal)
            {
                Object v = current.getCapability().getPropertiesAsMap().get(Resource.VERSION);

                // If there is no version, then select the resource
                // with the greatest number of capabilities.
                if ((v == null) && (bestVersion == null)
                    && (best.getResource().getCapabilities().length
                        < current.getResource().getCapabilities().length))
                {
                    best = current;
                    bestLocal = isCurrentLocal;
                }
                else if ((v != null) && (v instanceof Version))
                {
                    // If there is no best version or if the current
                    // resource's version is lower, then select it.
                    if ((bestVersion == null) || (bestVersion.compareTo(v) < 0))
                    {
                        best = current;
                        bestLocal = isCurrentLocal;
                        bestVersion = (Version) v;
                    }
                    // If the current resource version is equal to the
                    // best
                    else if (bestVersion.compareTo(v) == 0)
                    {
                        // If the symbolic name is the same, use the highest
                        // bundle version.
                        if ((best.getResource().getSymbolicName() != null)
                            && best.getResource().getSymbolicName().equals(
                                current.getResource().getSymbolicName()))
                        {
                            if (best.getResource().getVersion().compareTo(
                                current.getResource().getVersion()) < 0)
                            {
                                best = current;
                                bestLocal = isCurrentLocal;
                                bestVersion = (Version) v;
                            }
                        }
                        // Otherwise select the one with the greatest
                        // number of capabilities.
                        else if (best.getResource().getCapabilities().length
                            < current.getResource().getCapabilities().length)
                        {
                            best = current;
                            bestLocal = isCurrentLocal;
                            bestVersion = (Version) v;
                        }
                    }
                }
            }
        }

        return (best == null) ? null : best;
    }

    private static void checkInterrupt()
    {
        if (Thread.interrupted())
        {
            throw new org.apache.felix.bundlerepository.InterruptedResolutionException();
        }
    }

    public synchronized void deploy(int flags)
    {
//        m_deployFlags = flags;
        // Must resolve if not already resolved.
        if (!m_resolved && !resolve(flags))
        {
            m_logger.log(Logger.LOG_ERROR, "Resolver: Cannot resolve target resources.");
            return;
        }

        // Check to make sure that our local state cache is up-to-date
        // and error if it is not. This is not completely safe, because
        // the state can still change during the operation, but we will
        // be optimistic. This could also be made smarter so that it checks
        // to see if the local state changes overlap with the resolver.
        for (int repoIdx = 0; (m_repositories != null) && (repoIdx < m_repositories.length); repoIdx++)
        {
            if (m_repositories[repoIdx].getLastModified() > m_resolveTimeStamp)
            {
                throw new IllegalStateException("Framework state has changed, must resolve again.");
            }
        }

        // Eliminate duplicates from target, required, optional resources.
        Map<Resource, Resource> deployMap = new HashMap<Resource, Resource>();
        Resource[] resources = getAddedResources();
        for (int i = 0; (resources != null) && (i < resources.length); i++)
        {
            deployMap.put(resources[i], resources[i]);
        }
        resources = getRequiredResources();
        for (int i = 0; (resources != null) && (i < resources.length); i++)
        {
            deployMap.put(resources[i], resources[i]);
        }
        if ((flags & NO_OPTIONAL_RESOURCES) == 0)
        {
            resources = getOptionalResources();
            for (int i = 0; (resources != null) && (i < resources.length); i++)
            {
                deployMap.put(resources[i], resources[i]);
            }
        }
        Resource[] deployResources = deployMap.keySet().toArray(new Resource[deployMap.size()]);

        // List to hold all resources to be started.
        List<Bundle> startList = new ArrayList<Bundle>();

        // Deploy each resource, which will involve either finding a locally
        // installed resource to update or the installation of a new version
        // of the resource to be deployed.
        for (int i = 0; i < deployResources.length; i++)
        {
            // For the resource being deployed, see if there is an older
            // version of the resource already installed that can potentially
            // be updated.
            LocalResourceImpl localResource =
                findUpdatableLocalResource(deployResources[i]);
            // If a potentially updatable older version was found,
            // then verify that updating the local resource will not
            // break any of the requirements of any of the other
            // resources being deployed.
            if ((localResource != null) &&
                isResourceUpdatable(localResource, deployResources[i], deployResources))
            {
                // Only update if it is a different version.
                if (!localResource.equals(deployResources[i]))
                {
                    // Update the installed bundle.
                    try
                    {
                        // stop the bundle before updating to prevent
                        // the bundle update from throwing due to not yet
                        // resolved dependencies
                        boolean doStartBundle = (flags & START) != 0;
                        if (localResource.getBundle().getState() == Bundle.ACTIVE)
                        {
                            doStartBundle = true;
                            localResource.getBundle().stop();
                        }

                        localResource.getBundle().update(FileUtil.openURL(new URL(deployResources[i].getURI())));

                        // If necessary, save the updated bundle to be
                        // started later.
                        if (doStartBundle)
                        {
                            Bundle bundle = localResource.getBundle();
                            if (!isFragmentBundle(bundle))
                            {
                                startList.add(bundle);
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        m_logger.log(
                            Logger.LOG_ERROR,
                            "Resolver: Update error - " + getBundleName(localResource.getBundle()),
                            ex);
                        return;
                    }
                }
            }
            else
            {
                // Install the bundle.
                try
                {
                    // Perform the install, but do not use the actual
                    // bundle JAR URL for the bundle location, since this will
                    // limit OBR's ability to manipulate bundle versions. Instead,
                    // use a unique timestamp as the bundle location.
                    URL url = new URL(deployResources[i].getURI());
                    Bundle bundle = m_context.installBundle(
                        "obr://"
                        + deployResources[i].getSymbolicName()
                        + "/-" + System.currentTimeMillis(),
                        FileUtil.openURL(url));

                    // If necessary, save the installed bundle to be
                    // started later.
                    if ((flags & START) != 0)
                    {
                        if (!isFragmentBundle(bundle))
                        {
                            startList.add(bundle);
                        }
                    }
                }
                catch (Exception ex)
                {
                    m_logger.log(
                        Logger.LOG_ERROR,
                        "Resolver: Install error - " + deployResources[i].getSymbolicName(),
                        ex);
                    return;
                }
            }
        }

        for (int i = 0; i < startList.size(); i++)
        {
            try
            {
                startList.get(i).start();
            }
            catch (BundleException ex)
            {
                m_logger.log(
                    Logger.LOG_ERROR,
                    "Resolver: Start error - " + startList.get(i).getSymbolicName(),
                    ex);
            }
        }
    }

    /**
     * Determines if the given bundle is a fragement bundle.
     *
     * @param bundle bundle to check
     * @return flag indicating if the given bundle is a fragement
     */
    private static boolean isFragmentBundle(Bundle bundle)
    {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }

    // TODO: OBR - Think about this again and make sure that deployment ordering
    // won't impact it...we need to update the local state too.
    private LocalResourceImpl findUpdatableLocalResource(Resource resource)
    {
        // Determine if any other versions of the specified resource
        // already installed.
        Resource[] localResources = findLocalResources(resource.getSymbolicName());
        if (localResources != null)
        {
            // Since there are local resources with the same symbolic
            // name installed, then we must determine if we can
            // update an existing resource or if we must install
            // another one. Loop through all local resources with same
            // symbolic name and find the first one that can be updated
            // without breaking constraints of existing local resources.
            for (int i = 0; i < localResources.length; i++)
            {
                if (isResourceUpdatable(localResources[i], resource, localResources))
                {
                    return (LocalResourceImpl) localResources[i];
                }
            }
        }
        return null;
    }

    /**
     * Returns all local resources with the given symbolic name.
     * @param symName The symbolic name of the wanted local resources.
     * @return The local resources with the specified symbolic name.
     */
    private Resource[] findLocalResources(String symName)
    {
        Resource[] localResources = getResources(true);

        List<Resource> matchList = new ArrayList<Resource>();
        for (int i = 0; i < localResources.length; i++)
        {
            String localSymName = localResources[i].getSymbolicName();
            if ((localSymName != null) && localSymName.equals(symName))
            {
                matchList.add(localResources[i]);
            }
        }
        return matchList.toArray(new Resource[matchList.size()]);
    }

    private static boolean isResourceUpdatable(
        Resource oldVersion, Resource newVersion, Resource[] resources)
    {
        // Get all of the local resolvable requirements for the old
        // version of the resource from the specified resource array.
        Requirement[] reqs = getResolvableRequirements(oldVersion, resources);
        if (reqs == null)
        {
            return true;
        }

        // Now make sure that all of the requirements resolved by the
        // old version of the resource can also be resolved by the new
        // version of the resource.
        Capability[] caps = newVersion.getCapabilities();
        if (caps == null)
        {
            return false;
        }
        for (int reqIdx = 0; reqIdx < reqs.length; reqIdx++)
        {
            boolean satisfied = false;
            for (int capIdx = 0; !satisfied && (capIdx < caps.length); capIdx++)
            {
                if (reqs[reqIdx].isSatisfied(caps[capIdx]))
                {
                    satisfied = true;
                }
            }

            // If any of the previously resolved requirements cannot
            // be resolved, then the resource is not updatable.
            if (!satisfied)
            {
                return false;
            }
        }

        return true;
    }

    private static Requirement[] getResolvableRequirements(Resource resource, Resource[] resources)
    {
        // For the specified resource, find all requirements that are
        // satisfied by any of its capabilities in the specified resource
        // array.
        Capability[] caps = resource.getCapabilities();
        if ((caps != null) && (caps.length > 0))
        {
            List<Requirement> reqList = new ArrayList<Requirement>();
            for (int capIdx = 0; capIdx < caps.length; capIdx++)
            {
                boolean added = false;
                for (int resIdx = 0; !added && (resIdx < resources.length); resIdx++)
                {
                    Requirement[] reqs = resources[resIdx].getRequirements();
                    for (int reqIdx = 0;
                        (reqs != null) && (reqIdx < reqs.length);
                        reqIdx++)
                    {
                        if (reqs[reqIdx].isSatisfied(caps[capIdx]))
                        {
                            added = true;
                            reqList.add(reqs[reqIdx]);
                        }
                    }
                }
            }
            return reqList.toArray(new Requirement[reqList.size()]);
        }
        return null;
    }

    public static String getBundleName(Bundle bundle)
    {
        String name = (String) bundle.getHeaders().get(Constants.BUNDLE_NAME);
        return (name == null)
            ? "Bundle " + Long.toString(bundle.getBundleId())
            : name;
    }

}
