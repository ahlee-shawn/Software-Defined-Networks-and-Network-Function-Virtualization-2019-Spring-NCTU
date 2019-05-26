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
package leeang6969.lab6;

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
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.util.KryoNamespace;
import org.onlab.packet.IPv4;
import org.onlab.graph.Vertex;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.Description;
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
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.host.HostService;
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

import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import javafx.util.Pair;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

	private final Logger log = LoggerFactory.getLogger(getClass());
	
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected PacketService packetService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected FlowRuleService flowRuleService;

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

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected NetworkConfigRegistry cfgService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected CoreService coreService;

	private static String DHCP_server_ID = "default_DHCP_server_ID";
	private static String DHCP_server_PORT = "default_DHCP_server_PORT";
	private static String DHCP_server_MAC = "default_DHCP_server_MAC";
	private static String DHCP_server_IP = "default_DHCP_server_IP";
	private ApplicationId appId;
	private MyPacketProcessor processor = new MyPacketProcessor();
	private static ArrayList<TopologyVertex> shortestPath = new ArrayList<TopologyVertex>();
	public TopologyVertex source_switch;
	public TopologyVertex destination_switch;

	@Activate
	protected void activate() {
		appId = coreService.registerApplication("leeang6969.lab6");
		cfgService.addListener(cfgListener);
		factories.forEach(cfgService::registerConfigFactory);
		cfgListener.reconfigureNetwork(cfgService.getConfig(appId, leeang6969.lab6.MyConfig.class));
		packetService.addProcessor(processor, PacketProcessor.director(2));
		requestIntercepts();
		log.info("Started");
	}

	@Deactivate
	protected void deactivate() {
		cfgService.removeListener(cfgListener);
		factories.forEach(cfgService::unregisterConfigFactory);
		packetService.removeProcessor(processor);
		flowRuleService.removeFlowRulesById(appId);
		log.info("Stopped");
	}

	private final InternalConfigListener cfgListener = new InternalConfigListener();
	private final Set<ConfigFactory> factories = ImmutableSet.of(
			new ConfigFactory<ApplicationId, leeang6969.lab6.MyConfig>(APP_SUBJECT_FACTORY, leeang6969.lab6.MyConfig.class, "myconfig") {
				@Override
				public leeang6969.lab6.MyConfig createConfig() {
					return new leeang6969.lab6.MyConfig();
				}
			}
	);

	private class InternalConfigListener implements NetworkConfigListener {
		/**
		 * Reconfigures variable "myName" when there's new valid configuration uploaded.
		 * @param cfg configuration object
		 */
		private void reconfigureNetwork(leeang6969.lab6.MyConfig cfg) {
			if (cfg == null) {
				return;
			}
			if (cfg.DHCP_server_ID() != null && cfg.DHCP_server_PORT() != null && cfg.DHCP_server_MAC() != null && cfg.DHCP_server_IP() != null) {
				DHCP_server_ID = cfg.DHCP_server_ID();
				DHCP_server_PORT = cfg.DHCP_server_PORT();
				DHCP_server_MAC = cfg.DHCP_server_MAC();
				DHCP_server_IP = cfg.DHCP_server_IP();
			}
		}
		/**
		 * To handle the config event(s).
		 * @param event config event
		*/
		@Override
		public void event(NetworkConfigEvent event) {
			// While configuration is uploaded, update the variable "DHCP_server_ID", "DHCP_server_PORT", "DHCP_server_MAC", "DHCP_server_IP".
			if ((event.type() == NetworkConfigEvent.Type.CONFIG_ADDED ||
					event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED) &&
					event.configClass().equals(leeang6969.lab6.MyConfig.class)) {
				leeang6969.lab6.MyConfig cfg = cfgService.getConfig(appId, leeang6969.lab6.MyConfig.class);
				reconfigureNetwork(cfg);
				log.info("Reconfigured, new DHCP server ID is {}", DHCP_server_ID);
				log.info("Reconfigured, new DHCP server PORT is {}", DHCP_server_PORT);
				log.info("Reconfigured, new DHCP server MAC is {}", DHCP_server_MAC);
				log.info("Reconfigured, new DHCP server IP is {}", DHCP_server_IP);
			}
		}
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
			Topology topo = topologyService.currentTopology();
			TopologyGraph graph = topologyService.getGraph(topo);

			InboundPacket pkt = context.inPacket();
			Ethernet ethPkt = pkt.parsed();
			MacAddress sourceMac = ethPkt.getSourceMAC();
			MacAddress destinationMac = ethPkt.getDestinationMAC();
			MacAddress macFlood = MacAddress.valueOf("FF:FF:FF:FF:FF:FF");
			MacAddress macDHCP = MacAddress.valueOf(DHCP_server_MAC);

			IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
			IpAddress sourceIp = IpAddress.valueOf(ipv4Packet.getSourceAddress());
			IpAddress destinationIp = IpAddress.valueOf(ipv4Packet.getDestinationAddress());
			IpAddress ipZero = IpAddress.valueOf(IPv4.toIPv4Address("0.0.0.0"));
			IpAddress ipFlood = IpAddress.valueOf(IPv4.toIPv4Address("255.255.255.255"));
			IpPrefix ipDHCP_prefix = IpPrefix.valueOf(DHCP_server_IP);
			IpPrefix ipZero_prefix = IpPrefix.valueOf("0.0.0.0/32");
			IpPrefix ipFlood_prefix = IpPrefix.valueOf("255.255.255.255/32");

			for(int i = 0; i < 100000000; i++){
				// time for controller to get topology correctly
			}

			log.info("Get a IPv4 packet");

			if (sourceIp.equals(ipZero) && destinationIp.equals(ipFlood) && destinationMac.equals(macFlood)){
				log.info("Get a DHCP Discover packet");
				for(TopologyVertex v: graph.getVertexes()){
					if(v.deviceId().equals(DeviceId.deviceId(DHCP_server_ID))){
						destination_switch = v;
					}
					for(Host h: hostService.getConnectedHosts(v.deviceId())){
						if(h.mac().equals(sourceMac)){
							source_switch = v;
						}
					}
				}

				ArrayList<TopologyVertex> path = bfs(graph, source_switch, destination_switch);
				for(int i = 0; i < path.size(); i++){
					log.info("{}", path.get(i).deviceId().toString());
				}

				// flows from server to host
				for(int i = 0; i < path.size(); i++){
					if(i == 0){
						for(Host h: hostService.getHostsByMac(sourceMac)){
							PortNumber portNumber = h.location().port();
							TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
							selectorBuilder.matchEthType((short)2048);
							selectorBuilder.matchEthSrc(macDHCP);
							selectorBuilder.matchEthDst(sourceMac);
							selectorBuilder.matchIPSrc(ipDHCP_prefix);

							TrafficTreatment treatment = DefaultTrafficTreatment.builder()
			                	.setOutput(portNumber)
								.build();

							ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
				                .withSelector(selectorBuilder.build())
				                .withTreatment(treatment)
				                .withPriority(10)
				                .withFlag(ForwardingObjective.Flag.VERSATILE)
				                .fromApp(appId)
				                .makeTemporary(10)
								.add();

							flowObjectiveService.forward(path.get(i).deviceId(), forwardingObjective);
							break;
						}
					}
					for(Link l: linkService.getDeviceEgressLinks(path.get(i).deviceId())){
						if(i != 0 && l.dst().deviceId().equals(path.get(i - 1).deviceId())){
							PortNumber portNumber = l.src().port();
							TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
							selectorBuilder.matchEthType((short)2048);
							selectorBuilder.matchEthSrc(macDHCP);
							selectorBuilder.matchEthDst(sourceMac);
							selectorBuilder.matchIPSrc(ipDHCP_prefix);

							TrafficTreatment treatment = DefaultTrafficTreatment.builder()
			                	.setOutput(portNumber)
								.build();

							ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
				                .withSelector(selectorBuilder.build())
				                .withTreatment(treatment)
				                .withPriority(10)
				                .withFlag(ForwardingObjective.Flag.VERSATILE)
				                .fromApp(appId)
				                .makeTemporary(10)
								.add();

							flowObjectiveService.forward(l.src().deviceId(), forwardingObjective);
						}
					}
				}

				// flows from host to server
				for(int i = path.size() - 1; i >= 0; i--){
					if(i == path.size() - 1){
						PortNumber portNumber = PortNumber.portNumber(DHCP_server_PORT);
						TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
						selectorBuilder.matchEthType((short)2048);
						selectorBuilder.matchEthSrc(sourceMac);
						selectorBuilder.matchEthDst(destinationMac);
						selectorBuilder.matchIPSrc(ipZero_prefix);
						selectorBuilder.matchIPDst(ipFlood_prefix);

						TrafficTreatment treatment = DefaultTrafficTreatment.builder()
							.setOutput(portNumber)
							.build();

						ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
							.withSelector(selectorBuilder.build())
							.withTreatment(treatment)
							.withPriority(20)
							.withFlag(ForwardingObjective.Flag.VERSATILE)
							.fromApp(appId)
							.makeTemporary(10)
							.add();

						flowObjectiveService.forward(path.get(i).deviceId(), forwardingObjective);
					}
					for(Link l: linkService.getDeviceEgressLinks(path.get(i).deviceId())){
					
						if(i != path.size() - 1 && l.dst().deviceId().equals(path.get(i + 1).deviceId())){
							PortNumber portNumber = l.src().port();
							TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
							selectorBuilder.matchEthType((short)2048);
							selectorBuilder.matchEthSrc(sourceMac);
							selectorBuilder.matchEthDst(destinationMac);
							selectorBuilder.matchIPSrc(ipZero_prefix);
							selectorBuilder.matchIPDst(ipFlood_prefix);

							TrafficTreatment treatment = DefaultTrafficTreatment.builder()
								.setOutput(portNumber)
								.build();

							ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
								.withSelector(selectorBuilder.build())
								.withTreatment(treatment)
								.withPriority(20)
								.withFlag(ForwardingObjective.Flag.VERSATILE)
								.fromApp(appId)
								.makeTemporary(10)
								.add();

							flowObjectiveService.forward(l.src().deviceId(), forwardingObjective);
						}
					}
				}
				path.clear();
			}
		}
	}

	public static ArrayList<TopologyVertex> bfs(TopologyGraph graph, TopologyVertex source, TopologyVertex destination){
		ArrayList<TopologyVertex> path = new ArrayList<TopologyVertex>();
		if(source.equals(destination)){
			path.add(source);
			return path;
		}
		ArrayDeque<TopologyVertex> queue = new ArrayDeque<TopologyVertex>();
		ArrayDeque<TopologyVertex> visited = new ArrayDeque<TopologyVertex>();
		queue.offer(source);
		while(!queue.isEmpty()){
			TopologyVertex vertex = queue.poll();
			visited.offer(vertex);

			ArrayList<TopologyVertex> neighborList = new ArrayList<TopologyVertex>();
			for(TopologyEdge i: graph.getEdgesFrom(vertex)){
				neighborList.add(i.dst());
			}
			int index = 0;
			int neighborSize = neighborList.size();
			while(index != neighborSize){
				TopologyVertex neighbor = neighborList.get(index);
				path.add(neighbor);
				path.add(vertex);

				if(neighbor.equals(destination)){
					return processPath(source, destination, path);
				}else{
					if(!visited.contains(neighbor)){
						queue.offer(neighbor);
					}
				}
				index ++;
			}
		}
		return null;
	}
	public static ArrayList<TopologyVertex> processPath(TopologyVertex source, TopologyVertex destination, ArrayList<TopologyVertex> path){
		int index = path.indexOf(destination);
		TopologyVertex src = path.get(index + 1);
		shortestPath.add(0, destination);
		if(src.equals(source)){
			shortestPath.add(0, source);
			return shortestPath;
		}else{
			return processPath(source, src, path);
		}
	}
}