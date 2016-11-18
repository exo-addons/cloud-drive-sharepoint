/*
 * Copyright (C) 2003-2016 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.clouddrive.sharepoint;

import org.exoplatform.clouddrive.CloudDriveException;
import org.exoplatform.clouddrive.cmis.CMISUser;
import org.exoplatform.clouddrive.cmis.JCRLocalCMISDrive;
import org.exoplatform.clouddrive.jcr.NodeFinder;
import org.exoplatform.clouddrive.sharepoint.SharepointConnector.API;
import org.exoplatform.clouddrive.utils.ExtendedMimeTypeResolver;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Local drive for CMIS provider.<br>
 * 
 */
public class JCRLocalSharepointDrive extends JCRLocalCMISDrive {

  /**
   * The Class SharepointFileChange.
   */
  class SharepointFileChange extends FileChange {

    /**
     * Instantiates a new sharepoint file change.
     *
     * @param fileId the file id
     * @param orig the orig
     * @throws RepositoryException the repository exception
     * @throws CloudDriveException the cloud drive exception
     */
    SharepointFileChange(String fileId, FileChange orig) throws RepositoryException, CloudDriveException {
      super(orig.getChangeId(),
            orig.getPath(),
            fileId, // here we use given file ID
            orig.isFolder(),
            orig.getChangeType(),
            orig.getSynchronizer());
      if (orig.getFileUUID() != null) {
        this.fileUUID = orig.getFileUUID();
      }
      if (orig.getFilePath() != null) {
        this.filePath = orig.getFilePath();
      }
      if (orig.getNode() != null) {
        this.node = orig.getNode();
      }
      if (orig.getFile() != null) {
        this.file = orig.getFile(); // this file will have origin file ID
      }
      for (int i = 0; i < orig.getApplied(); i++) {
        this.applied.countDown();
      }
    }

  }

  /**
   * Instantiates a new JCR local sharepoint drive.
   *
   * @param user the user
   * @param driveNode the drive node
   * @param sessionProviders the session providers
   * @param finder the finder
   * @param mimeTypes the mime types
   * @param exoURL the exo URL
   * @throws CloudDriveException the cloud drive exception
   * @throws RepositoryException the repository exception
   */
  protected JCRLocalSharepointDrive(SharepointUser user,
                                    Node driveNode,
                                    SessionProviderService sessionProviders,
                                    NodeFinder finder,
                                    ExtendedMimeTypeResolver mimeTypes,
                                    String exoURL) throws CloudDriveException, RepositoryException {
    super(user, driveNode, sessionProviders, finder, mimeTypes, exoURL);
    SharepointAPI api = user.api();
    saveAccess(driveNode, api.getPassword(), api.getServiceURL(), api.getRepositoryId());
  }

  /**
   * Instantiates a new JCR local sharepoint drive.
   *
   * @param apiBuilder the api builder
   * @param driveNode the drive node
   * @param sessionProviders the session providers
   * @param finder the finder
   * @param mimeTypes the mime types
   * @param exoURL the exo URL
   * @throws RepositoryException the repository exception
   * @throws CloudDriveException the cloud drive exception
   */
  protected JCRLocalSharepointDrive(API apiBuilder,
                                    Node driveNode,
                                    SessionProviderService sessionProviders,
                                    NodeFinder finder,
                                    ExtendedMimeTypeResolver mimeTypes,
                                    String exoURL) throws RepositoryException, CloudDriveException {
    super(loadUser(apiBuilder, driveNode), driveNode, sessionProviders, finder, mimeTypes, exoURL);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void initDrive(Node driveNode) throws CloudDriveException, RepositoryException {
    super.initDrive(driveNode);

    // SharePoint specific info
    driveNode.setProperty("sharepoint:siteURL", getUser().getSiteURL());
    driveNode.setProperty("sharepoint:siteTitle", getUser().getSiteTitle());
    driveNode.setProperty("sharepoint:userLogin", getUser().getSiteUser().getLoginName());
    driveNode.setProperty("sharepoint:userTitle", getUser().getSiteUser().getTitle());
  }

  /**
   * Load user from the drive Node.
   *
   * @param apiBuilder {@link API} API builder
   * @param driveNode {@link Node} root of the drive
   * @return {@link CMISUser}
   * @throws RepositoryException the repository exception
   * @throws SharepointException the sharepoint exception
   * @throws CloudDriveException the cloud drive exception
   */
  protected static SharepointUser loadUser(API apiBuilder, Node driveNode) throws RepositoryException,
                                                                           SharepointException,
                                                                           CloudDriveException {
    SharepointUser user = (SharepointUser) JCRLocalCMISDrive.loadUser(apiBuilder, driveNode);
    return user;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SharepointUser getUser() {
    return (SharepointUser) user;
  }

  /**
   * Extract actual file ID without its version classifier in SP. If given file ID is <code>null</code> then
   * it will be returned as is.
   * 
   * @param fileId String
   * @return String with file ID
   */
  protected String simpleFileId(String fileId) {
    if (fileId != null) {
      String[] fidParts = fileId.split("-");
      if (fidParts.length > 1) {
        return fidParts[0]; // first part of ID like 65-512 (or 65-1024 and so on)
      }
    }
    return fileId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void cleanUpdated(String fileId) throws RepositoryException, CloudDriveException {
    super.cleanUpdated(simpleFileId(fileId));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void cleanRemoved(String fileId) throws RepositoryException, CloudDriveException {
    super.cleanRemoved(simpleFileId(fileId));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected synchronized void saveChanges(List<FileChange> changes) throws RepositoryException, CloudDriveException {
    List<FileChange> fileChanges = new ArrayList<FileChange>();
    for (FileChange ch : changes) {
      fileChanges.add(new SharepointFileChange(simpleFileId(ch.getFileId()), ch));
    }
    super.saveChanges(fileChanges);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected synchronized void commitChanges(Collection<FileChange> changes,
                                            Collection<FileChange> skipped) throws RepositoryException, CloudDriveException {
    List<FileChange> filesChanged = new ArrayList<FileChange>();
    for (FileChange ch : changes) {
      filesChanged.add(new SharepointFileChange(simpleFileId(ch.getFileId()), ch));
    }
    List<FileChange> filesSkipped = new ArrayList<FileChange>();
    for (FileChange ch : skipped) {
      filesSkipped.add(new SharepointFileChange(simpleFileId(ch.getFileId()), ch));
    }
    super.commitChanges(filesChanged, filesSkipped);
  }
}
