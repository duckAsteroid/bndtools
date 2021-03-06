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
package org.bndtools.core.obr.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.felix.bundlerepository.impl.PropertyImpl;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Repository XML xml based on StaX
 */
public class PullParser {

    public static final String REPOSITORY = "repository";
    public static final String NAME = "name";
    public static final String LASTMODIFIED = "lastmodified";
    public static final String REFERRAL = "referral";
    public static final String RESOURCE = "resource";
    public static final String DEPTH = "depth";
    public static final String URL = "url";
    public static final String CATEGORY = "category";
    public static final String ID = "id";
    public static final String CAPABILITY = "capability";
    public static final String REQUIRE = "require";
    public static final String P = "p";
    public static final String N = "n";
    public static final String T = "t";
    public static final String V = "v";
    public static final String FILTER = "filter";
    public static final String EXTEND = "extend";
    public static final String MULTIPLE = "multiple";
    public static final String OPTIONAL = "optional";

    public PullParser()
    {
    }

    public static RepositoryImpl parseRepository(InputStream is) throws Exception
    {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(is, null);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !REPOSITORY.equals(reader.getName()))
        {
            throw new Exception("Expected element 'repository' at the root of the document");
        }
        return parse(reader);
    }

    public static RepositoryImpl parseRepository(Reader r) throws Exception
    {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(r);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !REPOSITORY.equals(reader.getName()))
        {
            throw new Exception("Expected element 'repository' at the root of the document");
        }
        return parse(reader);
    }

    public static ResourceImpl parseResource(Reader r) throws Exception
    {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(r);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !RESOURCE.equals(reader.getName()))
        {
            throw new Exception("Expected element 'resource'");
        }
        return parseResource(reader);
    }

    public static CapabilityImpl parseCapability(Reader r) throws Exception
    {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(r);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !CAPABILITY.equals(reader.getName()))
        {
            throw new Exception("Expected element 'capability'");
        }
        return parseCapability(reader);
    }

    public static PropertyImpl parseProperty(Reader r) throws Exception
    {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(r);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !P.equals(reader.getName()))
        {
            throw new Exception("Expected element 'p'");
        }
        return parseProperty(reader);
    }

    public static RequirementImpl parseRequirement(Reader r) throws Exception
    {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(r);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !REQUIRE.equals(reader.getName()))
        {
            throw new Exception("Expected element 'require'");
        }
        return parseRequire(reader);
    }

    public static RepositoryImpl parse(XmlPullParser reader) throws Exception
    {
        RepositoryImpl repository = new RepositoryImpl();
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            String name = reader.getAttributeName(i);
            String value = reader.getAttributeValue(i);
            if (NAME.equals(name))
            {
                repository.setName(value);
            }
            else if (LASTMODIFIED.equals(name))
            {
                repository.setLastModified(value);
            }
        }
        int event;
        while ((event = reader.nextTag()) == XmlPullParser.START_TAG)
        {
            String element = reader.getName();
            if (REFERRAL.equals(element))
            {
                Referral referral = parseReferral(reader);
                repository.addReferral(referral);
            }
            else if (RESOURCE.equals(element))
            {
                ResourceImpl resource = parseResource(reader);
                repository.addResource(resource);
            }
            else
            {
                ignoreTag(reader);
            }
        }
        // Sanity check
        sanityCheckEndElement(reader, event, REPOSITORY);
        return repository;
    }

    private static void sanityCheckEndElement(XmlPullParser reader, int event, String element)
    {
        if (event != XmlPullParser.END_TAG || !element.equals(reader.getName()))
        {
            throw new IllegalStateException("Unexpected state while finishing element " + element);
        }
    }

    public static Referral parseReferral(XmlPullParser reader) throws Exception
    {
        Referral referral = new Referral();
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            String name = reader.getAttributeName(i);
            String value = reader.getAttributeValue(i);
            if (DEPTH.equals(name))
            {
                referral.setDepth(value);
            }
            else if (URL.equals(name))
            {
                referral.setUrl(value);
            }
        }
        sanityCheckEndElement(reader, reader.nextTag(), REFERRAL);
        return referral;
    }

    public static ResourceImpl parseResource(XmlPullParser reader) throws Exception
    {
        ResourceImpl resource = new ResourceImpl();
        try
        {
            for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
            {
                resource.put(reader.getAttributeName(i), reader.getAttributeValue(i));
            }
            int event;
            while ((event = reader.nextTag()) == XmlPullParser.START_TAG)
            {
                String element = reader.getName();
                if (CATEGORY.equals(element))
                {
                    String category = parseCategory(reader);
                    resource.addCategory(category);
                }
                else if (CAPABILITY.equals(element))
                {
                    CapabilityImpl capability = parseCapability(reader);
                    resource.addCapability(capability);
                }
                else if (REQUIRE.equals(element))
                {
                    RequirementImpl requirement = parseRequire(reader);
                    resource.addRequire(requirement);
                }
                else
                {
                    StringBuffer sb = null;
                    String type = reader.getAttributeValue(null, "type");
                    while ((event = reader.next()) != XmlPullParser.END_TAG)
                    {
                        switch (event)
                        {
                            case XmlPullParser.START_TAG:
                                throw new Exception("Unexpected element inside <require/> element");
                            case XmlPullParser.TEXT:
                                if (sb == null)
                                {
                                    sb = new StringBuffer();
                                }
                                sb.append(reader.getText());
                                break;
                        }
                    }
                    if (sb != null)
                    {
                        resource.put(element, sb.toString().trim(), type);
                    }
                }
            }
            // Sanity check
            if (event != XmlPullParser.END_TAG || !RESOURCE.equals(reader.getName()))
            {
                throw new Exception("Unexpected state");
            }
            return resource;
        }
        catch (Exception e)
        {
            throw new Exception("Error while parsing resource " + resource.getId() + " at line " + reader.getLineNumber() + " and column " + reader.getColumnNumber(), e);
        }
    }

    public static String parseCategory(XmlPullParser reader) throws IOException, XmlPullParserException
    {
        String id = null;
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            if (ID.equals(reader.getAttributeName(i)))
            {
                id = reader.getAttributeValue(i);
            }
        }
        sanityCheckEndElement(reader, reader.nextTag(), CATEGORY);
        return id;
    }

    public static CapabilityImpl parseCapability(XmlPullParser reader) throws Exception
    {
        CapabilityImpl capability = new CapabilityImpl();
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            String name = reader.getAttributeName(i);
            String value = reader.getAttributeValue(i);
            if (NAME.equals(name))
            {
                capability.setName(value);
            }
        }
        int event;
        while ((event = reader.nextTag()) == XmlPullParser.START_TAG)
        {
            String element = reader.getName();
            if (P.equals(element))
            {
                PropertyImpl prop = parseProperty(reader);
                capability.addProperty(prop);
            }
            else
            {
                ignoreTag(reader);
            }
        }
        // Sanity check
        sanityCheckEndElement(reader, event, CAPABILITY);
        return capability;
    }

    public static PropertyImpl parseProperty(XmlPullParser reader) throws Exception
    {
        String n = null, t = null, v = null;
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            String name = reader.getAttributeName(i);
            String value = reader.getAttributeValue(i);
            if (N.equals(name))
            {
                n = value;
            }
            else if (T.equals(name))
            {
                t = value;
            }
            else if (V.equals(name))
            {
                v = value;
            }
        }
        PropertyImpl prop = new PropertyImpl(n, t, v);
        // Sanity check
        sanityCheckEndElement(reader, reader.nextTag(), P);
        return prop;
    }

    public static RequirementImpl parseRequire(XmlPullParser reader) throws Exception
    {
        RequirementImpl requirement = new RequirementImpl();
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            String name = reader.getAttributeName(i);
            String value = reader.getAttributeValue(i);
            if (NAME.equals(name))
            {
                requirement.setName(value);
            }
            else if (FILTER.equals(name))
            {
                requirement.setFilter(value);
            }
            else if (EXTEND.equals(name))
            {
                requirement.setExtend(Boolean.parseBoolean(value));
            }
            else if (MULTIPLE.equals(name))
            {
                requirement.setMultiple(Boolean.parseBoolean(value));
            }
            else if (OPTIONAL.equals(name))
            {
                requirement.setOptional(Boolean.parseBoolean(value));
            }
        }
        int event;
        StringBuffer sb = null;
        while ((event = reader.next()) != XmlPullParser.END_TAG)
        {
            switch (event)
            {
                case XmlPullParser.START_TAG:
                    throw new Exception("Unexpected element inside <require/> element");
                case XmlPullParser.TEXT:
                    if (sb == null)
                    {
                        sb = new StringBuffer();
                    }
                    sb.append(reader.getText());
                    break;
            }
        }
        if (sb != null)
        {
            requirement.addText(sb.toString());
        }
        // Sanity check
        sanityCheckEndElement(reader, event, REQUIRE);
        return requirement;
    }

    public static void ignoreTag(XmlPullParser reader) throws IOException, XmlPullParserException {
        int level = 1;
        while (level > 0)
        {
            int eventType = reader.next();
            if (eventType == XmlPullParser.START_TAG)
            {
                level++;
            }
            else if (eventType == XmlPullParser.END_TAG)
            {
                level--;
            }
        }
    }
}