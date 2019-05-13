from mininet.net import Mininet

from mininet.node import Controller, RemoteController, OVSKernelSwitch, UserSwitch, OVSSwitch

from mininet.cli import CLI

from mininet.log import setLogLevel

from mininet.link import Link, TCLink

 

def topology():

        net = Mininet( controller=RemoteController, link=TCLink, switch=OVSKernelSwitch)

 

        # Add hosts and switches

        h1= net.addHost( 'h1', mac="00:00:00:00:00:01" )

        h2 = net.addHost( 'h2', mac="00:00:00:00:00:02" )

 

        s1 = net.addSwitch( 's1', protocols=["OpenFlow10,OpenFlow13"], listenPort=6634 )

        s2 = net.addSwitch( 's2', protocols=["OpenFlow10,OpenFlow13"], listenPort=6635 )

        s3 = net.addSwitch( 's3', protocols=["OpenFlow10,OpenFlow13"], listenPort=6636 )

        s4 = net.addSwitch( 's4', protocols=["OpenFlow10,OpenFlow13"], listenPort=6637 )

 

        c0 = net.addController( 'c0', controller=RemoteController, ip='127.0.0.1', port=6653 )

 

        net.addLink( h1, s1)

        net.addLink( h2, s4)

        net.addLink( s1, s2)

        net.addLink( s1, s3)

        net.addLink( s2, s4)

        net.addLink( s3, s4)

        net.build()

        c0.start()

        s1.start( [c0] )

        s2.start( [c0] )

        s3.start( [c0] )

        s4.start( [c0] )

 

        print "*** Running CLI"

        CLI( net )

 

        print "*** Stopping network"

        net.stop()

 

if __name__ == '__main__':

    setLogLevel( 'info' )

    topology()   
