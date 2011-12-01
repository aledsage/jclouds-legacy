/**
 * Licensed to jclouds, Inc. (jclouds) under one or more
 * contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  jclouds licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jclouds.aws.ec2.compute.config;

import static org.jclouds.Constants.PROPERTY_SESSION_INTERVAL;
import static org.jclouds.compute.domain.OsFamily.AMZN_LINUX;

import javax.inject.Named;
import javax.inject.Singleton;

import org.jclouds.aws.ec2.AWSEC2PropertiesBuilder;
import org.jclouds.aws.ec2.compute.AWSEC2TemplateBuilderImpl;
import org.jclouds.aws.ec2.compute.functions.AWSRunningInstanceToNodeMetadata;
import org.jclouds.aws.ec2.compute.predicates.AWSEC2InstancePresent;
import org.jclouds.aws.ec2.compute.strategy.AWSEC2CreateNodesInGroupThenAddToSet;
import org.jclouds.aws.ec2.compute.strategy.AWSEC2DestroyNodeStrategy;
import org.jclouds.aws.ec2.compute.strategy.AWSEC2GetNodeMetadataStrategy;
import org.jclouds.aws.ec2.compute.strategy.AWSEC2ListNodesStrategy;
import org.jclouds.aws.ec2.compute.strategy.AWSEC2ReviseParsedImage;
import org.jclouds.aws.ec2.compute.strategy.CreateKeyPairPlacementAndSecurityGroupsAsNeededAndReturnRunOptions;
import org.jclouds.aws.ec2.compute.suppliers.AWSEC2HardwareSupplier;
import org.jclouds.compute.config.BaseComputeServiceContextModule;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.ec2.compute.config.EC2BindComputeStrategiesByClass;
import org.jclouds.ec2.compute.config.EC2BindComputeSuppliersByClass;
import org.jclouds.ec2.compute.domain.RegionAndName;
import org.jclouds.ec2.compute.functions.RunningInstanceToNodeMetadata;
import org.jclouds.ec2.compute.internal.EC2TemplateBuilderImpl;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.jclouds.ec2.compute.predicates.InstancePresent;
import org.jclouds.ec2.compute.strategy.CreateKeyPairAndSecurityGroupsAsNeededAndReturnRunOptions;
import org.jclouds.ec2.compute.strategy.EC2CreateNodesInGroupThenAddToSet;
import org.jclouds.ec2.compute.strategy.EC2DestroyNodeStrategy;
import org.jclouds.ec2.compute.strategy.EC2GetNodeMetadataStrategy;
import org.jclouds.ec2.compute.strategy.EC2ListNodesStrategy;
import org.jclouds.ec2.compute.strategy.ReviseParsedImage;
import org.jclouds.ec2.compute.suppliers.EC2HardwareSupplier;
import org.jclouds.ec2.compute.suppliers.RegionAndNameToImageSupplier;
import org.jclouds.rest.suppliers.MemoizedRetryOnTimeOutButNotOnAuthorizationExceptionSupplier;

import com.google.common.base.Supplier;
import com.google.common.cache.Cache;
import com.google.inject.Injector;
import com.google.inject.Provides;

/**
 * 
 * @author Adrian Cole
 */
public class AWSEC2ComputeServiceContextModule extends BaseComputeServiceContextModule {
   @Override
   protected void configure() {
      super.configure();
      installDependencies();
      install(new EC2BindComputeStrategiesByClass());
      install(new EC2BindComputeSuppliersByClass());
      bind(ReviseParsedImage.class).to(AWSEC2ReviseParsedImage.class);
      bind(CreateKeyPairAndSecurityGroupsAsNeededAndReturnRunOptions.class).to(
               CreateKeyPairPlacementAndSecurityGroupsAsNeededAndReturnRunOptions.class);
      bind(EC2HardwareSupplier.class).to(AWSEC2HardwareSupplier.class);
      bind(EC2TemplateBuilderImpl.class).to(AWSEC2TemplateBuilderImpl.class);
      bind(EC2GetNodeMetadataStrategy.class).to(AWSEC2GetNodeMetadataStrategy.class);
      bind(EC2ListNodesStrategy.class).to(AWSEC2ListNodesStrategy.class);
      bind(EC2DestroyNodeStrategy.class).to(AWSEC2DestroyNodeStrategy.class);
      bind(InstancePresent.class).to(AWSEC2InstancePresent.class);
      bind(EC2CreateNodesInGroupThenAddToSet.class).to(AWSEC2CreateNodesInGroupThenAddToSet.class);
      bind(RunningInstanceToNodeMetadata.class).to(AWSRunningInstanceToNodeMetadata.class);
   }

   protected void installDependencies() {
      install(new AWSEC2ComputeServiceDependenciesModule());
   }

   @Provides
   @Singleton
   protected Supplier<Cache<RegionAndName, ? extends Image>> provideRegionAndNameToImageSupplierCache(
            @Named(PROPERTY_SESSION_INTERVAL) long seconds, final RegionAndNameToImageSupplier supplier) {
      // FIXME Doesn't need to be memoizing any more
      return new MemoizedRetryOnTimeOutButNotOnAuthorizationExceptionSupplier<Cache<RegionAndName, ? extends Image>>(
               authException, seconds, new Supplier<Cache<RegionAndName, ? extends Image>>() {
                  @Override
                  public Cache<RegionAndName, ? extends Image> get() {
                     return supplier.get();
                  }
               });
   }

   @Override
   protected TemplateBuilder provideTemplate(Injector injector, TemplateBuilder template) {
      return template.osFamily(AMZN_LINUX).os64Bit(true);
   }

   /**
    * With amazon linux 2011.09, ssh starts after package updates, which slows the boot process and
    * runs us out of ssh retries (context property {@code "jclouds.ssh.max-retries"}).
    * 
    * @see <a href="http://aws.amazon.com/amazon-linux-ami/latest-release-notes/" />
    * @see AWSEC2PropertiesBuilder#defaultProperties
    */
   @Override
   protected TemplateOptions provideTemplateOptions(Injector injector, TemplateOptions options) {
      return options.as(EC2TemplateOptions.class).userData("#cloud-config\nrepo_upgrade: none\n".getBytes());
   }
}
