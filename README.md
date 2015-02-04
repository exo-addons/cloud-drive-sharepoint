eXo Cloud Drive SharePoint Connector
====================================

Microsoft SharePoint connector for [eXo Cloud Drive](https://github.com/exo-addons/cloud-drive-extension). This connector depends on CMIS connector (part of Cloud Drive core add-on) and add SharePoint specific implementation of Cloud Drive features.  It consist of three modules: services, webapp and packaging. Result of packaging it is a binaries archived in zip-file. Use it when building from source and installing from local folder.

Installation
------------

Users of Platform 4.1 can simply install the add-on via *addon* tool from central catalog: select latest version, use "--unstable" key if want install latest development version. In Platform 4.0 you may install [Addons Manager](https://github.com/exoplatform/addons-manager) first and then do the same way, or [download](http://sourceforge.net/projects/exo/files/Addons/Cloud%20Drive/) the connector binaries manually and install by _extension.sh_ tool. 

Before installing this connector to your eXo Platform server, install eXo Cloud Drive add-on itself. 

Configuration
-------------

SharePoint connector doesn't require any configuration for getting started. But you may find useful to predefine your SharePoint servers for better user experience.

Predefined services a part of Cloud Drive add-on and they can be configured via connector plugin. This method good on development level, when need add predefined services to the packaged connector (e.g. when extending a connector).
The CMIS connector, a base of SharePoint connector, additionally allows configure predefined AtomPub bindings via settings in eXo properties. Below both ways described with sample configuration.

#### Adding predefined services via connector plugin ####
Create eXo container configuration file and add _CloudDriveService_ component plugin there as shown below. Place host name and port of your SharePoint server in an URL, give a name to your predefined connection. This name, in conjunction with user name, later will be used for folder naming when connecting in eXo.

```xml
<!-- SharePoint connector plugin -->
  <external-component-plugins>
    <target-component>org.exoplatform.clouddrive.CloudDriveService</target-component>
    <component-plugin>
      <name>add.clouddriveprovider</name>
      <set-method>addPlugin</set-method>
      <type>org.exoplatform.clouddrive.sharepoint.SharepointConnector</type>
      <init-params>
        <object-param>
          <name>predefined-services</name>
          <object type="org.exoplatform.clouddrive.CloudDriveConnector$PredefinedServices">
            <field name="services">
              <collection type="java.util.LinkedHashSet">
                <value>
                  <object type="org.exoplatform.clouddrive.cmis.CMISProvider$AtomPub">
                    <field name="name">
                      <string>Marketing</string>
                    </field>
                    <field name="url">
                      <string>http://marketing.acme.com:8000/_vti_bin/cmis/rest?getRepositories</string>
                    </field>
                  </object>
                </value>
                <value>
                  <object type="org.exoplatform.clouddrive.cmis.CMISProvider$AtomPub">
                    <field name="name">
                      <string>Sales</string>
                    </field>
                    <field name="url">
                      <string>http://sales.acme.com:8000/_vti_bin/cmis/rest?getRepositories</string>
                    </field>
                  </object>
                </value>
                <value>
                  <object type="org.exoplatform.clouddrive.cmis.CMISProvider$AtomPub">
                    <field name="name">
                      <string>BCG - US</string>
                    </field>
                    <field name="url">
                      <string>https://circle.bcghq.com/_vti_bin/cmis/rest?getRepositories</string>
                    </field>
                  </object>
                </value>
              </collection>
            </field>
          </object>
        </object-param>
      </init-params>
    </component-plugin>
  </external-component-plugins>
```

Save this file as *configuration.xml* in your eXo Platform configuration directory (known as _exo.conf.dir_), for Tomcat bundle it is by default _gatein/conf/portal/${PORTAL_NAME}/configuration.xml_ (where ${PORTAL_NAME} is a portal container name, *portal* by default). If you already have *configuration.xml* in configuration directory, then rename the SharePoint connector add-on configuration to something else and import it from your config file.

```xml

<import>file:/${exo.conf.dir}/portla/portal/cloud-drive-configuration.xml</import>

```

#### Adding predefined services in eXo properties ####
The same effect as via connector plugin, but much simpler, possible via setings in eXo properties file. On Platform 4.0 you'll need a new property to existing _configuration.properties_, on Platform 4.1 create (if not already done) your own _exo.properties_ and add described property to it.

```ini
clouddrive.sharepoint.predefined=Marketing:http://marketing.acme.com:8000/_vti_bin/cmis/rest?getRepositories\n\
Sales:http://sales.acme.com:8000/_vti_bin/cmis/rest?getRepositories\n\
BCG - US:https://circle.bcghq.com/_vti_bin/cmis/rest?getRepositories

```

Predefined service consists of a string started with a name of service and its URL followed after a colon. Several services can be configured: each service should be split from others by a new line character (\n). Service name cannot contain a colon in name (everything between it and \n or end of line will be treated as an URL). eXo properties it is Java Property file. Follow its markup rules when creating settings: escape with '\', split in multiline also by '\'. 

Additionally, via eXo properties, it's possible to avoid using predefined services from connector plugin configuration. Set _override_ flag as below to reset predefined SharePoint connector servers. 

```ini
clouddrive.sharepoint.predefined.override=false
```
Note that settings keys (on the left) are case-sensitive and must be in lower case. For more details about predefined services configuration refer to Cloud Drive documentation.
 

Development
-----------

For connector development refer to [Connector API](https://github.com/exo-addons/cloud-drive-extension/blob/master/documentation/CONNECTOR_API.md) documentation. 


