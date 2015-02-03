eXo Cloud Drive SharePoint Connector
====================================

Microsoft SharePoint connector for [eXo Cloud Drive](https://github.com/exo-addons/cloud-drive-extension). This connector depends on CMIS connector and add SharePoint specific implementation of Cloud Drive features.  It consist of three modules: services, webapp and packaging. Result of packaging it is a binaries archived in zip-file. Use it when building from source and installing from local folder.

Installation
------------

Users of Platform 4.1 can simply install the add-on via *addon* tool from central catalog: select latest version, use "--unstable" key if want install latest development version. In Platform 4.0 you may install [Addons Manager](https://github.com/exoplatform/addons-manager) first and then do the same way, or [download](http://sourceforge.net/projects/exo/files/Addons/Cloud%20Drive/) the connector binaries manually and install by _extension.sh_ tool. 

Before installing this connector to your eXo Platform server, install eXo Cloud Drive add-on itself. 

Configuration
-------------

SharePoint connector doesn't require any configuration for getting started. But you may find useful to pre-configure your SharePoint URL for better user experience.

Create eXo container configuration file and add a component plugin there as follows below. Place host name and port of your SharePoint server in an URL, give a name to your predefined connection. This name, in conjunction with user name, later will be used for folder naming when connecting in eXo.

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

Development
-----------

For connector development refer to [Connector API](https://github.com/exo-addons/cloud-drive-extension/blob/master/documentation/CONNECTOR_API.md) documentation. 


