/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package leeanghsuan.lab3;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onlab.packet.MacAddress;
import org.onlab.packet.Ethernet;
import org.onlab.util.KryoNamespace;
import org.onlab.packet.IPv4;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;

import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.WallClockTimestamp;
import org.onosproject.store.service.MultiValuedTimestamp;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.serializers.KryoNamespaces;

import java.util.HashSet;
import java.util.Iterator;
import javafx.util.Pair;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {
	private static final int DEFAULT_PRIORITY = 10;

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected PacketService packetService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected StorageService storageService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected CoreService coreService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected FlowObjectiveService flowObjectiveService;

	private ApplicationId appId;
	
	private EventuallyConsistentMap<Pair<DeviceId,MacAddress>, PortNumber> table;
	private MyPacketProcessor processor = new MyPacketProcessor();

	@Activate
	protected void activate() {
		KryoNamespace.Builder metricSerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
				.register(MultiValuedTimestamp.class);
		table =  storageService.<Pair<DeviceId,MacAddress>, PortNumber>eventuallyConsistentMapBuilder()
                .withName("macAddress-table")
                .withSerializer(metricSerializer)
                .withTimestampProvider((key, metricsData) -> new
						MultiValuedTimestamp<>(new WallClockTimestamp(), System.nanoTime()))
				.build();
		packetService.addProcessor(processor, PacketProcessor.director(2));
		appId = coreService.registerApplication("leeanghsuan.lab3");
		requestIntercepts();
		log.info("Started");
	}

	@Deactivate
	protected void deactivate() {
		packetService.removeProcessor(processor);
		log.info("Stopped");
	}

	private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
	}

	private class MyPacketProcessor implements PacketProcessor {
		@Override
		public void process(PacketContext context) {
			
			if (context.isHandled()) {
                return;
			}
			InboundPacket pkt = context.inPacket();
			Ethernet ethPkt = pkt.parsed();
			MacAddress sourceMac = ethPkt.getSourceMAC();
			MacAddress destinationMac = ethPkt.getDestinationMAC();
			DeviceId device = pkt.receivedFrom().deviceId();
			PortNumber inport = pkt.receivedFrom().port();
			
			Pair <DeviceId, MacAddress> packetSource = new Pair <DeviceId, MacAddress> (device, sourceMac); 
			// Check whether inport data is in table
			if(!table.containsKey(packetSource)) {
				log.info("Hi");
				table.put(packetSource, inport);
				log.info(table.keySet().toString());
				log.info(table.values().toString());
				log.info("Done");
			}

			Pair <DeviceId, MacAddress> packetDestination = new Pair <DeviceId, MacAddress> (device, destinationMac);
			// Check whether output data is in table
			if(!table.containsKey(packetDestination)) {
				// output data is not in table
				log.info("flood");
				context.treatmentBuilder().setOutput(PortNumber.FLOOD);
				context.send();
			}else{
				log.info("Contains");
				PortNumber outport = table.get(packetDestination);
				context.treatmentBuilder().setOutput(outport);
				context.send();

				TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
				selectorBuilder.matchEthDst(ethPkt.getDestinationMAC());

				TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                	.setOutput(outport)
					.build();

				ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
	                .withSelector(selectorBuilder.build())
	                .withTreatment(treatment)
	                .withPriority(10)
	                .withFlag(ForwardingObjective.Flag.VERSATILE)
	                .fromApp(appId)
					.add();

				flowObjectiveService.forward(device, forwardingObjective);

				log.info("Sent");
			}
		}
	}
}