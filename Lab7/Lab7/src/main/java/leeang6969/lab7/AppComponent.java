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
package leeang6969.lab7;

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
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpAddress.Version;
import org.onlab.packet.ARP;
import org.onlab.packet.VlanId;
import org.onlab.util.KryoNamespace;
import org.onlab.graph.Vertex;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.Description;
import org.onosproject.net.DefaultPort;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyVertex;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.LinkDescription;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.Link;
import org.onosproject.net.Port;
import org.onosproject.net.ElementId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.host.HostService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Device.Type;
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
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;

import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.WallClockTimestamp;
import org.onosproject.store.service.MultiValuedTimestamp;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.serializers.KryoNamespaces;

import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import javafx.util.Pair;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.nio.ByteBuffer;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected PacketService packetService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected StorageService storageService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected FlowRuleService flowRuleService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected CoreService coreService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected FlowObjectiveService flowObjectiveService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected TopologyService topologyService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected HostService hostService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected LinkService linkService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected DeviceService deviceService;

	private ApplicationId appId;

	private DeviceId deviceOutput;
	
	private EventuallyConsistentMap<IpAddress, MacAddress> ip_mac_table;
	private EventuallyConsistentMap<IpAddress, DeviceId> ip_id_table;
	private EventuallyConsistentMap<Pair<IpAddress, DeviceId>, PortNumber> ip_id_port_table;
	private MyPacketProcessor processor = new MyPacketProcessor();

	@Activate
	protected void activate() {
		KryoNamespace.Builder metricSerializer = KryoNamespace.newBuilder()
				.register(KryoNamespaces.API)
				.register(MultiValuedTimestamp.class);
		ip_id_table = storageService.<IpAddress, DeviceId>eventuallyConsistentMapBuilder()
				.withName("IpAddress-DeviceID-table")
				.withSerializer(metricSerializer)
				.withTimestampProvider((key, metricsData) -> new MultiValuedTimestamp<>(new WallClockTimestamp(), System.nanoTime()))
				.build();
		ip_id_port_table = storageService.<Pair<IpAddress, DeviceId>, PortNumber>eventuallyConsistentMapBuilder()
				.withName("IpAddress-DeviceID-PortNumber-table")
				.withSerializer(metricSerializer)
				.withTimestampProvider((key, metricsData) -> new MultiValuedTimestamp<>(new WallClockTimestamp(), System.nanoTime()))
				.build();
		ip_mac_table = storageService.<IpAddress, MacAddress>eventuallyConsistentMapBuilder()
				.withName("IpAddress-macAddress-table")
				.withSerializer(metricSerializer)
				.withTimestampProvider((key, metricsData) -> new MultiValuedTimestamp<>(new WallClockTimestamp(), System.nanoTime()))
				.build();
		packetService.addProcessor(processor, PacketProcessor.director(2));
		appId = coreService.registerApplication("leeang6969.lab7");
		requestIntercepts();
		log.info("Started");
	}

	@Deactivate
	protected void deactivate() {
		packetService.removeProcessor(processor);
		flowRuleService.removeFlowRulesById(appId);
		log.info("Stopped");
	}

	private void requestIntercepts() {
		TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
		selector.matchEthType(Ethernet.TYPE_ARP);
		packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
	}

	private class MyPacketProcessor implements PacketProcessor {
		@Override
		public void process(PacketContext context) {
			if (context.isHandled()) {
				return;
			}

			Topology topo = topologyService.currentTopology();
			TopologyGraph graph = topologyService.getGraph(topo);

			InboundPacket pkt = context.inPacket();
			DeviceId device = pkt.receivedFrom().deviceId();
			PortNumber inport = pkt.receivedFrom().port();

			Ethernet ethPkt = pkt.parsed();
			MacAddress sourceMac = ethPkt.getSourceMAC();
			MacAddress destinationMac = ethPkt.getDestinationMAC();
			MacAddress macFlood = MacAddress.valueOf("FF:FF:FF:FF:FF:FF");

			ARP ipv4Packet = (ARP) ethPkt.getPayload();
			IpAddress sourceIp = IpAddress.valueOf(IpAddress.Version.valueOf("INET"), ipv4Packet.getSenderProtocolAddress());
			IpAddress destinationIp = IpAddress.valueOf(IpAddress.Version.valueOf("INET"), ipv4Packet.getTargetProtocolAddress());

			Pair <IpAddress, DeviceId> packetSource = new Pair <IpAddress, DeviceId> (sourceIp, device); 

			// Check if source Mac Address is in the IpAddress-macAddress-table
			if(!ip_mac_table.containsKey(sourceIp)){
				ip_mac_table.put(sourceIp, sourceMac);
				log.info("Source IpAddress, Mac Address added to the IpAddress-macAddress-table");
			}

			// Check if source Mac Address is in the IpAddress-DeviceID-table
			if(!ip_id_table.containsKey(sourceIp)){
				ip_id_table.put(sourceIp, device);
				log.info("Source IpAddress, DeviceId added to the IpAddress-DeviceID-table");
			}

			// Check if source Mac Address is in the IpAddress-DeviceID-PortNumber-table
			if(!ip_id_port_table.containsKey(packetSource)){
				ip_id_port_table.put(packetSource, inport);
				log.info("Source IpAddress, DeviceID, PortNumber added to the IpAddress-DeviceID-PortNumber-table");
			}

			// Get ARP Request Packet
			if(ipv4Packet.getOpCode() == (short)1){
				if(destinationMac.equals(macFlood)){
					// Destination Mac Address in IpAddress-macAddress-table
					log.info("Get ARP Request Packet");
					if(ip_mac_table.containsKey(destinationIp)){
						log.info("Destination Mac Address in IpAddress-macAddress-table");
						MacAddress outputMac = ip_mac_table.get(destinationIp);
						Ethernet arpReply = ARP.buildArpReply(destinationIp.getIp4Address(), outputMac, ethPkt);

						TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(inport).build();
						OutboundPacket packet = new DefaultOutboundPacket(device, treatment, ByteBuffer.wrap(arpReply.serialize()));
						packetService.emit(packet);

						log.info("Destination Mac found in IpAddress-macAddress-table");
					}else{
						log.info("Destination Mac Address not in IpAddress-macAddress-table");
						// Destination Mac Address not in IpAddress-macAddress-table

						Ethernet arpRequest = ARP.buildArpRequest(sourceMac.toBytes(), sourceIp.toOctets(), destinationIp.toOctets(), VlanId.NO_VID);

						for(TopologyVertex v: graph.getVertexes()){
							log.info("Device ID: {}", v.deviceId().toString());
							List<DefaultPort> ports = new ArrayList<DefaultPort>();
							ports = (List<DefaultPort>)(Object)deviceService.getPorts(v.deviceId());
							List<PortNumber> outputPortList = new ArrayList<PortNumber>();
							int temp = 1;
							for(Iterator<DefaultPort> iter = ports.iterator(); iter.hasNext();){
								DefaultPort temp1 = iter.next();
								for(Link l: linkService.getDeviceEgressLinks(v.deviceId())){
									if(temp1.number().equals(l.src().port())){
										log.info("same");
										temp = 0;
									}
								}
								if(temp == 1){
									outputPortList.add(temp1.number());
								}
							}
							if(outputPortList.size()!=0){
								for(PortNumber p: outputPortList){
									if(p.toString() != "LOCAL"){
										if(!(v.deviceId().equals(device) && p.equals(inport))){
											TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(p).build();
											OutboundPacket packet = new DefaultOutboundPacket(v.deviceId(), treatment, ByteBuffer.wrap(arpRequest.serialize()));
											packetService.emit(packet);
											log.info("Packet sent {}", v.deviceId().toString());
											log.info("Port {}", p.toString());
										}
									}
								}
							}
						}
					}
				}else{
					log.info("ARP Request with Destination Mac Address Not Flooding");
					MacAddress outputMac = ip_mac_table.get(destinationIp);
					Ethernet arpReply = ARP.buildArpReply(destinationIp.getIp4Address(), outputMac, ethPkt);

					TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(inport).build();
					OutboundPacket packet = new DefaultOutboundPacket(device, treatment, ByteBuffer.wrap(arpReply.serialize()));
					packetService.emit(packet);

					log.info("Destination Mac found in IpAddress-macAddress-table");
				}
			}else{
				// Get ARP Reply Packet
				log.info("Get ARP Reply Packet");
				deviceOutput = ip_id_table.get(destinationIp);
				Pair <IpAddress, DeviceId> packet_temp2 = new Pair <IpAddress, DeviceId> (destinationIp, deviceOutput);
				PortNumber outputport = ip_id_port_table.get(packet_temp2);

				TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(outputport).build();
				OutboundPacket packet = new DefaultOutboundPacket(deviceOutput, treatment, ByteBuffer.wrap(ethPkt.duplicate().serialize()));
				packetService.emit(packet);
			}
			
		}
	}

}
