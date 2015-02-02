eXo Cloud Drive SharePoint Connector
====================================

Microsoft SharePoint connector project for eXo Cloud Drive. This connector depends on CMIS connector and add SharePoint specific implementation of Cloud Drive features.  It consist of three modules: services, webapp and packaging. 

Before installing this connector to your eXo Platform server, install eXo Cloud Drive add-on itself. 

SharePoint connector doesn't require any configuration for getting started. But you may find useful to pre-configure your SharePoint URL for better user experience.

Create eXo container configuration file and add a component plugin there as follows below. Place host name and port of your SharePoint server in an URL, give a name to your predefined connection. This name, in conjunction with user name, later will be used for folder naming when connecting in eXo.

```
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


For connector development find more info in [Connector API](https://github.com/exo-addons/cloud-drive-extension/blob/master/documentation/CONNECTOR_API.md). 


