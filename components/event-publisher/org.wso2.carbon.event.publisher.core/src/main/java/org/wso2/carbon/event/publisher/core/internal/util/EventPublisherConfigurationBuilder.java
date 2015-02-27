/*
*  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.event.publisher.core.internal.util;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.event.publisher.core.MessageType;
import org.wso2.carbon.event.publisher.core.adapter.OutputEventAdapterDto;
import org.wso2.carbon.event.publisher.core.OutputEventAdaptorService;
import org.wso2.carbon.event.publisher.core.exception.EventPublisherConfigurationException;
import org.wso2.carbon.event.publisher.core.exception.EventPublisherStreamValidationException;
import org.wso2.carbon.event.publisher.core.exception.EventPublisherValidationException;
import org.wso2.carbon.event.publisher.core.internal.ds.EventPublisherServiceValueHolder;
import org.wso2.carbon.event.publisher.core.internal.util.helper.EventPublisherConfigurationHelper;
import org.wso2.carbon.event.stream.manager.core.EventStreamService;
import org.wso2.carbon.event.stream.manager.core.exception.EventStreamConfigurationException;

import javax.xml.namespace.QName;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class EventPublisherConfigurationBuilder {

    private static boolean validateStreamDetails(String streamName, String streamVersion,
                                                 int tenantId)
            throws EventPublisherConfigurationException {

        EventStreamService eventStreamService = EventPublisherServiceValueHolder.getEventStreamService();
        try {
            StreamDefinition streamDefinition = eventStreamService.getStreamDefinition(streamName, streamVersion, tenantId);
            if (streamDefinition != null) {
                return true;
            }
        } catch (EventStreamConfigurationException e) {
            throw new EventPublisherConfigurationException("Error while validating stream definition with store : " + e.getMessage(), e);
        }
        return false;

    }

    private static boolean validateEventAdaptor(String eventAdaptorType) {


        //TODO , need to change this map to see when adaptor is loaded or not..

        OutputEventAdaptorService outputEventAdaptorService = EventPublisherServiceValueHolder.getOutputEventAdaptorService();
        List<OutputEventAdapterDto> eventAdaptorInfoList = outputEventAdaptorService.getEventAdaptors();

        if (eventAdaptorInfoList == null || eventAdaptorInfoList.size() == 0) {
            throw new EventPublisherValidationException("Event adaptor with type: " + eventAdaptorType + " does not exist", eventAdaptorType);
        }

        Iterator<OutputEventAdapterDto> eventAdaIteratorInfoIterator = eventAdaptorInfoList.iterator();
        for (; eventAdaIteratorInfoIterator.hasNext(); ) {
            OutputEventAdapterDto eventAdaptorInfo = eventAdaIteratorInfoIterator.next();
            if (eventAdaptorInfo.getEventAdaptorTypeName().equals(eventAdaptorType)) {
                return true;
            }
        }

        return false;
    }

    private static boolean validateSupportedMapping(String eventAdaptorType,
                                                    String messageType) {

        OutputEventAdaptorService eventAdaptorService = EventPublisherServiceValueHolder.getOutputEventAdaptorService();
        OutputEventAdapterDto eventAdaptorDto = eventAdaptorService.getEventAdaptorDto(eventAdaptorType);

        if (eventAdaptorDto == null) {
            throw new EventPublisherValidationException("Event Adaptor with type: " + eventAdaptorType + " does not exist", eventAdaptorType);
        }
        List<String> supportedOutputMessageTypes = eventAdaptorDto.getSupportedMessageTypes();
        return supportedOutputMessageTypes.contains(messageType);

    }


    public static EventPublisherConfiguration getEventPublisherConfiguration(
            OMElement eventPublisherConfigOMElement, int tenantId, String mappingType)
            throws EventPublisherConfigurationException, EventPublisherValidationException {

        EventPublisherConfiguration eventPublisherConfiguration = new EventPublisherConfiguration();

        OMElement fromElement = eventPublisherConfigOMElement.getFirstChildWithName(new QName(EventPublisherConstants.EF_CONF_NS, EventPublisherConstants.EF_ELE_FROM_PROPERTY));
        OMElement mappingElement = eventPublisherConfigOMElement.getFirstChildWithName(new QName(EventPublisherConstants.EF_CONF_NS, EventPublisherConstants.EF_ELE_MAPPING_PROPERTY));
        OMElement endpointElement = eventPublisherConfigOMElement.getFirstChildWithName(new QName(EventPublisherConstants.EF_CONF_NS, EventPublisherConstants.EF_ELE_ENDPOINT_PROPERTY));

        String fromStreamName = fromElement.getAttributeValue(new QName(EventPublisherConstants.EF_ATTR_STREAM_NAME));
        String fromStreamVersion = fromElement.getAttributeValue(new QName(EventPublisherConstants.EF_ATTR_VERSION));

        String endpointAdaptorType = endpointElement.getAttributeValue(new QName(EventPublisherConstants.EF_ATTR_TA_TYPE));

        if (!validateEventAdaptor(endpointAdaptorType)) {
            throw new EventPublisherValidationException("Event Adaptor with type: " + endpointAdaptorType + " does not exist", endpointAdaptorType);
        }

        if (!validateStreamDetails(fromStreamName, fromStreamVersion, tenantId)) {
            throw new EventPublisherStreamValidationException("Stream " + fromStreamName + ":" + fromStreamVersion + " does not exist", fromStreamName + ":" + fromStreamVersion);
        }

        EndpointAdaptorPropertyConfiguration outputEventAdaptorConfiguration = EventPublisherConfigurationHelper.getOutputEventAdaptorConfiguration(endpointAdaptorType);
        OutputAdaptorConfiguration outputAdaptorConfiguration = new OutputAdaptorConfiguration();

        //TODO put a proper name
        outputAdaptorConfiguration.setAdaptorName(eventPublisherConfiguration.getEventPublisherName());
        outputAdaptorConfiguration.setEndpointType(endpointAdaptorType);

        Iterator toElementPropertyIterator = endpointElement.getChildrenWithName(
                new QName(EventPublisherConstants.EF_CONF_NS, EventPublisherConstants.EF_ELE_PROPERTY)
        );

        while (toElementPropertyIterator.hasNext()) {
            OMElement toElementProperty = (OMElement) toElementPropertyIterator.next();
            String propertyName = toElementProperty.getAttributeValue(new QName(EventPublisherConstants.EF_ATTR_NAME));
            String propertyValue = toElementProperty.getText();
            outputEventAdaptorConfiguration.addEventAdaptorProperty(propertyName, propertyValue);
        }

        outputAdaptorConfiguration.setEndpointAdaptorPropertyConfiguration(outputEventAdaptorConfiguration);


        if (mappingType.equalsIgnoreCase(EventPublisherConstants.EF_WSO2EVENT_MAPPING_TYPE)) {
            if (!validateSupportedMapping(endpointAdaptorType, MessageType.WSO2EVENT)) {
                throw new EventPublisherConfigurationException("WSO2Event Mapping is not supported by event adaptor type " + endpointAdaptorType);
            }
        } else if (mappingType.equalsIgnoreCase(EventPublisherConstants.EF_TEXT_MAPPING_TYPE)) {
            if (!validateSupportedMapping(endpointAdaptorType, MessageType.TEXT)) {
                throw new EventPublisherConfigurationException("Text Mapping is not supported by event adaptor type " + endpointAdaptorType);
            }
        } else if (mappingType.equalsIgnoreCase(EventPublisherConstants.EF_MAP_MAPPING_TYPE)) {
            if (!validateSupportedMapping(endpointAdaptorType, MessageType.MAP)) {
                throw new EventPublisherConfigurationException("Map Mapping is not supported by event adaptor type " + endpointAdaptorType);
            }
        } else if (mappingType.equalsIgnoreCase(EventPublisherConstants.EF_XML_MAPPING_TYPE)) {
            if (!validateSupportedMapping(endpointAdaptorType, MessageType.XML)) {
                throw new EventPublisherConfigurationException("XML Mapping is not supported by event adaptor type " + endpointAdaptorType);
            }
        } else if (mappingType.equalsIgnoreCase(EventPublisherConstants.EF_JSON_MAPPING_TYPE)) {
            if (!validateSupportedMapping(endpointAdaptorType, MessageType.JSON)) {
                throw new EventPublisherConfigurationException("JSON Mapping is not supported by event adaptor type " + endpointAdaptorType);
            }
        } else {
            String factoryClassName = getMappingTypeFactoryClass(mappingElement);
            if (factoryClassName == null) {
                throw new EventPublisherConfigurationException("Corresponding mappingType " + mappingType + " is not valid");
            }

            Class factoryClass;
            try {
                factoryClass = Class.forName(factoryClassName);
                OutputMapperFactory outputMapperFactory = (OutputMapperFactory) factoryClass.newInstance();
                EventPublisherServiceValueHolder.getMappingFactoryMap().putIfAbsent(mappingType, outputMapperFactory);
            } catch (ClassNotFoundException e) {
                throw new EventPublisherConfigurationException("Class not found exception occurred ", e);
            } catch (InstantiationException e) {
                throw new EventPublisherConfigurationException("Instantiation exception occurred ", e);
            } catch (IllegalAccessException e) {
                throw new EventPublisherConfigurationException("Illegal exception occurred ", e);
            }
        }


        eventPublisherConfiguration.setEventPublisherName(eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.EF_ATTR_NAME)));

        if (eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.TM_ATTR_STATISTICS)) != null && eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.TM_ATTR_STATISTICS)).equals(EventPublisherConstants.TM_VALUE_ENABLE)) {
            eventPublisherConfiguration.setEnableStatistics(true);
        } else if (eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.TM_ATTR_STATISTICS)) != null && eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.TM_ATTR_STATISTICS)).equals(EventPublisherConstants.TM_VALUE_DISABLE)) {
            eventPublisherConfiguration.setEnableStatistics(false);
        }

        if (eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.TM_ATTR_TRACING)) != null && eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.TM_ATTR_TRACING)).equals(EventPublisherConstants.TM_VALUE_ENABLE)) {
            eventPublisherConfiguration.setEnableTracing(true);
        } else if (eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.TM_ATTR_TRACING)) != null && eventPublisherConfigOMElement.getAttributeValue(new QName(EventPublisherConstants.TM_ATTR_TRACING)).equals(EventPublisherConstants.TM_VALUE_DISABLE)) {
            eventPublisherConfiguration.setEnableTracing(false);
        }

        eventPublisherConfiguration.setFromStreamName(fromStreamName);
        eventPublisherConfiguration.setFromStreamVersion(fromStreamVersion);
        eventPublisherConfiguration.setOutputMapping(EventPublisherServiceValueHolder.getMappingFactoryMap().get(mappingType).constructOutputMapping(mappingElement));
        eventPublisherConfiguration.setOutputAdaptorConfiguration(outputAdaptorConfiguration);
        return eventPublisherConfiguration;

    }

    public static OMElement eventPublisherConfigurationToOM(
            EventPublisherConfiguration eventPublisherConfiguration)
            throws EventPublisherConfigurationException {

        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMElement eventFormatterConfigElement = factory.createOMElement(new QName(
                EventPublisherConstants.EF_ELE_ROOT_ELEMENT));
        eventFormatterConfigElement.declareDefaultNamespace(EventPublisherConstants.EF_CONF_NS);

        eventFormatterConfigElement.addAttribute(EventPublisherConstants.EF_ATTR_NAME, eventPublisherConfiguration.getEventPublisherName(), null);

        if (eventPublisherConfiguration.isEnableStatistics()) {
            eventFormatterConfigElement.addAttribute(EventPublisherConstants.TM_ATTR_STATISTICS, EventPublisherConstants.TM_VALUE_ENABLE,
                    null);
        } else if (!eventPublisherConfiguration.isEnableStatistics()) {
            eventFormatterConfigElement.addAttribute(EventPublisherConstants.TM_ATTR_STATISTICS, EventPublisherConstants.TM_VALUE_DISABLE,
                    null);
        }

        if (eventPublisherConfiguration.isEnableTracing()) {
            eventFormatterConfigElement.addAttribute(EventPublisherConstants.TM_ATTR_TRACING, EventPublisherConstants.TM_VALUE_ENABLE,
                    null);
        } else if (!eventPublisherConfiguration.isEnableTracing()) {
            eventFormatterConfigElement.addAttribute(EventPublisherConstants.TM_ATTR_TRACING, EventPublisherConstants.TM_VALUE_DISABLE,
                    null);
        }

        //From properties - Stream Name and version
        OMElement fromPropertyElement = factory.createOMElement(new QName(
                EventPublisherConstants.EF_ELE_FROM_PROPERTY));
        fromPropertyElement.declareDefaultNamespace(EventPublisherConstants.EF_CONF_NS);
        fromPropertyElement.addAttribute(EventPublisherConstants.EF_ATTR_STREAM_NAME, eventPublisherConfiguration.getFromStreamName(), null);
        fromPropertyElement.addAttribute(EventPublisherConstants.EF_ATTR_VERSION, eventPublisherConfiguration.getFromStreamVersion(), null);
        eventFormatterConfigElement.addChild(fromPropertyElement);

        OMElement mappingOMElement = EventPublisherServiceValueHolder.getMappingFactoryMap().get(eventPublisherConfiguration.getOutputMapping().getMappingType()).constructOutputMappingOM(eventPublisherConfiguration.getOutputMapping(), factory);

        eventFormatterConfigElement.addChild(mappingOMElement);


        OMElement toOMElement = factory.createOMElement(new QName(
                EventPublisherConstants.EF_ELE_ENDPOINT_PROPERTY));
        toOMElement.declareDefaultNamespace(EventPublisherConstants.EF_CONF_NS);

        OutputAdaptorConfiguration outputAdaptorConfiguration = eventPublisherConfiguration.getOutputAdaptorConfiguration();
        toOMElement.addAttribute(EventPublisherConstants.EF_ATTR_TA_TYPE, outputAdaptorConfiguration.getEndpointType(), null);

        Map<String, String> eventPropertyMap = outputAdaptorConfiguration.getEndpointAdaptorProperties();
        for (Map.Entry<String, String> propertyEntry : eventPropertyMap.entrySet()) {
            OMElement propertyElement = factory.createOMElement(new QName(
                    EventPublisherConstants.EF_ELE_PROPERTY));
            propertyElement.declareDefaultNamespace(EventPublisherConstants.EF_CONF_NS);
            propertyElement.addAttribute(EventPublisherConstants.EF_ATTR_NAME, propertyEntry.getKey(), null);
            propertyElement.setText(propertyEntry.getValue());
            toOMElement.addChild(propertyElement);
        }

        eventFormatterConfigElement.addChild(toOMElement);
        return eventFormatterConfigElement;
    }

    public static String getMappingTypeFactoryClass(OMElement omElement) {
        return omElement.getAttributeValue(new QName(EventPublisherConstants.EF_ATTR_FACTORY_CLASS));
    }


}
