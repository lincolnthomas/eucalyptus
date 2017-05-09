/*************************************************************************
 * (c) Copyright 2017 Hewlett Packard Enterprise Development Company LP
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 *************************************************************************/

package com.eucalyptus.blockstorage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.eucalyptus.blockstorage.ceph.CephRbdAdapter;
import com.eucalyptus.blockstorage.ceph.CephRbdFormatTwoAdapter;
import com.eucalyptus.blockstorage.ceph.entities.CephRbdImageToBeDeleted;
import com.eucalyptus.blockstorage.ceph.entities.CephRbdInfo;
import com.eucalyptus.blockstorage.ceph.entities.CephRbdSnapshotToBeDeleted;
import com.eucalyptus.blockstorage.util.StorageProperties;
import com.eucalyptus.entities.Transactions;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import edu.ucsb.eucalyptus.util.SystemUtil;
import edu.ucsb.eucalyptus.util.SystemUtil.CommandOutput;

public class CephCleaner {

  private static final Logger LOG = Logger.getLogger(CephCleaner.class);

  private static final Function<CephRbdImageToBeDeleted, String> IMAGE_NAME_FUNCTION = new Function<CephRbdImageToBeDeleted, String>() {
    @Override
    public String apply(CephRbdImageToBeDeleted arg0) {
      return arg0.getImageName();
    }
  };

  private static Set<String> accessiblePools = Sets.newHashSet();
  
  private static CephRbdAdapter rbdService = null;
  private static CephRbdInfo cephInfo = new CephRbdInfo();
//  private static DatabaseDestination dest = null;
  
  public static void main(String[] args) {
    System.out.println("CephCleaner " + OffsetDateTime.now().toString() + ": Starting one-time cleaning of Ceph already-deleted images (volumes) and snapshots.");
    LOG.info("Starting one-time cleaning of Ceph already-deleted images (volumes) and snapshots.");

/*    try {
      StandalonePersistence.setupInitProviders();
      StandalonePersistence.eucaHome="/";
      StandalonePersistence.setupSystemProperties();
      
      dest = ( DatabaseDestination ) ClassLoader.getSystemClassLoader( ).loadClass( "com.eucalyptus.upgrade.PostgresqlDestination" ).newInstance( );
      dest.initialize( );
*/
      String[] cmd = new String[] {StorageProperties.EUCA_ROOT_WRAPPER, "psql", "-h", "/var/lib/eucalyptus/db/data/", "-p", "8777", 
          "eucalyptus_shared", "-t", "-c",
          "select ceph_config_file,ceph_keyring_file,ceph_snapshot_pools,ceph_user,ceph_volume_pools,cluster_name,deleted_image_prefix,virsh_secret from eucalyptus_storage.ceph_rbd_info"};
      LOG.debug("Executing: " + Joiner.on(" ").skipNulls().join(cmd));
      CommandOutput output = null;
      try {
        output = SystemUtil.runWithRawOutput(cmd);
      } catch (Exception e) {
        LOG.error("Error executing psql command:",e);
      }
      if (output == null) {
        LOG.error("No output from psql command, exiting.");
        return;
      }
      LOG.debug("Dump from rbd command:\nReturn value=" + output.returnValue + "\nOutput=" + output.output + "\nDebug=" + output.error);
      String[] cephRbdInfoOutput = output.output.split("[|]");
      cephInfo.setCephConfigFile(cephRbdInfoOutput[0].trim());
      cephInfo.setCephKeyringFile(cephRbdInfoOutput[1].trim());
      cephInfo.setCephSnapshotPools(cephRbdInfoOutput[2].trim());
      cephInfo.setCephUser(cephRbdInfoOutput[3].trim());
      cephInfo.setCephVolumePools(cephRbdInfoOutput[4].trim());
      cephInfo.setClusterName(cephRbdInfoOutput[5].trim());
      cephInfo.setDeletedImagePrefix(cephRbdInfoOutput[6].trim());
      cephInfo.setVirshSecret(cephRbdInfoOutput[7].trim());
      
      LOG.debug("cephInfo is " + cephInfo);
/*    } catch (Exception e) {
      LOG.info("CephCleaner Exception: ", e);
    }
*/

/*
    LOG.info("Initializing CephInfo entity!");
    cephInfo = CephRbdInfo.getStorageInfo();
*/
    LOG.debug("Deleted prefix is " + cephInfo.getDeletedImagePrefix() + ", volume pools are " + cephInfo.getCephVolumePools());
    
    LOG.info("Initializing Ceph RBD service provider");
    rbdService = new CephRbdFormatTwoAdapter(cephInfo);

    Splitter COMMA_SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();

    accessiblePools.addAll(COMMA_SPLITTER.splitToList(cephInfo.getCephVolumePools()));
    accessiblePools.addAll(COMMA_SPLITTER.splitToList(cephInfo.getCephSnapshotPools()));

    LOG.info("Cleaning up already-deleted images (volumes) in Ceph RBD pools.");
    cleanDeletedCephImages();
/*
    LOG.info("Cleaning up already-deleted snapshots in Ceph RBD pools.");
    cleanDeletedCephSnapshots();
*/

    LOG.info("Finished one-time cleaning of Ceph already-deleted images (volumes) and snapshots.");
    System.out.println("CephCleaner " + OffsetDateTime.now().toString() + ": Finished one-time cleaning of Ceph already-deleted images (volumes) and snapshots.");
  }  // end main

  private static void cleanDeletedCephImages () {
    try {
      for (final String pool : accessiblePools) { // Cycle through all pools
        try {
/*          CephRbdImageToBeDeleted search = new CephRbdImageToBeDeleted().withPoolName(pool);

          // Get the images that were marked for deletion from the database
          final List<String> imagesToBeCleaned = Transactions.transform(search, IMAGE_NAME_FUNCTION);
*/
          // Invoke clean up
          rbdService.cleanUpImages(pool, cephInfo.getDeletedImagePrefix(), null);

/*          
          // Delete database records after call to rbd succeeds
          if (imagesToBeCleaned != null && !imagesToBeCleaned.isEmpty()) {
            Transactions.deleteAll(search, new Predicate<CephRbdImageToBeDeleted>() {
              @Override
              public boolean apply(CephRbdImageToBeDeleted arg0) {
                return imagesToBeCleaned.contains(arg0.getImageName());
              }
            });
          }
*/
        } catch (Throwable t) {
          LOG.debug("Encountered error while cleaning up images in pool " + pool, t);
        }
      }
    } catch (Exception e) {
      LOG.debug("Ignoring exception during clean up of images marked for deletion", e);
    }
  }  // end cleanDeletedCephImages


  private static void cleanDeletedCephSnapshots () {

    try {
      for (final String pool : accessiblePools) { // Cycle through all pools
        try {
          CephRbdSnapshotToBeDeleted search = new CephRbdSnapshotToBeDeleted().withPool(pool);

          // Get the images that were marked for deletion from the database
          List<CephRbdSnapshotToBeDeleted> listToBeDeleted = Transactions.findAll(search);

          if (listToBeDeleted != null && !listToBeDeleted.isEmpty()) {
            SetMultimap<String, String> toBeDeleted = Multimaps.newSetMultimap(Maps.newHashMap(), new Supplier<Set<String>>() {

              @Override
              public Set<String> get() {
                return Sets.newHashSet();
              }
            });

            // Organize stuff into a multimap
            for (CephRbdSnapshotToBeDeleted r : listToBeDeleted) {
              toBeDeleted.put(r.getImage(), r.getSnapshot());
            }

            // Invoke clean up
            SetMultimap<String, String> cantBeDeleted = rbdService.cleanUpSnapshots(pool, toBeDeleted);

            // Delete database records for all except those that couldn't be cleaned up
            Transactions.deleteAll(search, new Predicate<CephRbdSnapshotToBeDeleted>() {
              @Override
              public boolean apply(CephRbdSnapshotToBeDeleted arg0) {
                return toBeDeleted.containsEntry(arg0.getImage(), arg0.getSnapshot())
                    && !cantBeDeleted.containsEntry(arg0.getImage(), arg0.getSnapshot());
              }
            });

          } else {
            // nothing to do here, no snaps to be deleted in this pool
          }
        } catch (Throwable t) {
          LOG.debug("Encountered error while cleaning up rbd snapshots in pool " + pool, t);
        }
      }
    } catch (Exception e) {
      LOG.debug("Ignoring exception during clean up of rbd snapshots marked for deletion", e);
    }
  }  // end cleanDeletedCephSnapshots

}  // end class CephCleaner
