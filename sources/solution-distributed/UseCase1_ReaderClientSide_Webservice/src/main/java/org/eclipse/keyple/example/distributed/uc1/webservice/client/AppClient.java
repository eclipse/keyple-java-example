/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.example.distributed.uc1.webservice.client;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.keyple.core.service.ConfigurableReader;
import org.eclipse.keyple.core.service.Plugin;
import org.eclipse.keyple.core.service.Reader;
import org.eclipse.keyple.core.service.SmartCardServiceProvider;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.eclipse.keyple.core.util.protocol.ContactlessCardCommonProtocol;
import org.eclipse.keyple.distributed.LocalServiceClient;
import org.eclipse.keyple.distributed.LocalServiceClientFactory;
import org.eclipse.keyple.distributed.LocalServiceClientFactoryBuilder;
import org.eclipse.keyple.example.distributed.uc1.webservice.util.UserInputDataDto;
import org.eclipse.keyple.example.distributed.uc1.webservice.util.UserOutputDataDto;
import org.eclipse.keyple.plugin.pcsc.PcscPluginFactoryBuilder;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.pcsc.PcscSupportedContactlessProtocol;
import org.eclipse.keyple.plugin.stub.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Example of a client side application. */
@ApplicationScoped
public class AppClient {

  private static final Logger logger = LoggerFactory.getLogger(AppClient.class);
  private static final String LOCAL_SERVICE_NAME = "LOCAL_SERVICE_#1";

  /** The endpoint client */
  @Inject @RestClient EndpointClient endpointClient;

  /** The local plugin */
  private Plugin plugin;

  /** The local reader */
  private Reader reader;

  /**
   * Initialize the client components :
   *
   * <ul>
   *   <li>A {@link StubPlugin} with a {@link StubReader} that accepts {@link
   *       org.eclipse.keyple.plugin.stub.StubSmartCard} based on protocol ISO14443_4,
   *   <li>A {@link LocalServiceClient} with a sync node bind to a {@link
   *       org.eclipse.keyple.distributed.spi.SyncEndpointClientSpi} endpoint.
   * </ul>
   */
  public void init() {

    // Init a local plugin and reader with a stub reader.
    initStubReader();

    // Init a local plugin and reader with a PCSC reader.
    // initPcscReader();

    // Init the local service factory.
    LocalServiceClientFactory factory =
        LocalServiceClientFactoryBuilder.builder(LOCAL_SERVICE_NAME)
            .withSyncNode(endpointClient)
            .build();

    // Init the local service using the associated factory.
    SmartCardServiceProvider.getService().registerDistributedLocalService(factory);
  }

  /**
   * Executes a simple scenario : insert a card and invokes a remote service.
   *
   * @return true if the transaction was successful
   */
  public Boolean launchScenario() {

    // Builds the user input data if needed.
    UserInputDataDto userInputData = new UserInputDataDto().setUserId("test");

    // Retrieves the local service.
    LocalServiceClient localService =
        SmartCardServiceProvider.getService()
            .getDistributedLocalService(LOCAL_SERVICE_NAME)
            .getExtension(LocalServiceClient.class);

    // Executes on the local reader the remote ticketing service having the id
    // "EXECUTE_CALYPSO_SESSION_FROM_REMOTE_SELECTION".
    UserOutputDataDto output =
        localService.executeRemoteService(
            "EXECUTE_CALYPSO_SESSION_FROM_REMOTE_SELECTION",
            reader.getName(),
            null,
            userInputData,
            UserOutputDataDto.class);

    return output.isSuccessful();
  }

  /** Init a local plugin and reader with a stub reader and an inserted card */
  private void initStubReader() {

    // Registers the plugin to the smart card service.
    plugin =
        SmartCardServiceProvider.getService()
            .registerPlugin(StubPluginFactoryBuilder.builder().build());

    // Plug the reader manually to the plugin.
    plugin.getExtension(StubPlugin.class).plugReader("stubReader", true, null);

    // sleep for a moment to let the readers being detected
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Retrieves the connected reader from the plugin.
    reader = plugin.getReader("stubReader");

    // Activates the protocol ISO_14443_4 on the reader.
    ((ConfigurableReader) reader)
        .activateProtocol(
            ContactlessCardCommonProtocol.ISO_14443_4.name(),
            ContactlessCardCommonProtocol.ISO_14443_4.name());

    // Insert a stub card manually on the reader.
    ((StubReader) reader).insertCard(getStubCard());

    logger.info(
        "Client - Local reader was configured with STUB reader : {} with a card", reader.getName());
  }

  /**
   * Init a local plugin and reader with a PCSC reader whose name is PCSC_READER_NAME and an
   * inserted card.
   */
  private void initPcscReader() {

    // Registers the plugin to the smart card service.
    plugin =
        SmartCardServiceProvider.getService()
            .registerPlugin(PcscPluginFactoryBuilder.builder().build());

    if (plugin.getReaders().size() != 1) {
      throw new IllegalStateException(
          "For the matter of this example, we expect one and only one PCSC reader to be connected");
    }

    // Retrieves the connected reader from the plugin.
    reader = plugin.getReaders().iterator().next();

    if (!reader.isCardPresent()) {

      throw new IllegalStateException(
          "For the matter of this example, we expect a card to be present at the startup");
    }

    // Sets PCSC specific configuration to handle contactless.
    reader.getExtension(PcscReader.class).setIsoProtocol(PcscReader.IsoProtocol.T1);

    // Activates the protocol ISO_14443_4 on the reader.
    ((ConfigurableReader) reader)
        .activateProtocol(
            PcscSupportedContactlessProtocol.ISO_14443_4.name(),
            ContactlessCardCommonProtocol.ISO_14443_4.name());

    logger.info(
        "Client - Local reader was configured with PCSC reader : {} with a card", reader.getName());
  }

  /**
   * Returns a new instance of a stub card.
   *
   * @return A new instance.
   */
  private StubSmartCard getStubCard() {
    return StubSmartCard.builder()
        .withPowerOnData(ByteArrayUtil.fromHex("3B8880010000000000718100F9"))
        .withProtocol(ContactlessCardCommonProtocol.ISO_14443_4.name())
        /* Select Application */
        .withSimulatedCommand("00A4 0400 05 AABBCCDDEE 00", "6A82")
        /* Select Application */
        .withSimulatedCommand(
            "00A4 0400 09 315449432E49434131 00",
            "6F238409315449432E49434131A516BF0C13C708 0000000011223344 53070A3C23121410019000")
        /* Read Records - EnvironmentAndHolder (SFI=07)) */
        .withSimulatedCommand(
            "00B2013C00", "24B92848080000131A50001200000000000000000000000000000000009000")
        /* Open Secure Session V3.1 */
        .withSimulatedCommand(
            "008A0B4104C1C2C3C400",
            "030490980030791D01112233445566778899AABBCCDDEEFF00112233445566778899AABBCC9000")
        /* Open Secure Session V3.1 */
        .withSimulatedCommand(
            "008A0B3904C1C2C3C400",
            "0308306C00307E1D24B928480800000606F0001200000000000000000000000000000000009000")
        /* Read Records */
        .withSimulatedCommand(
            "00B2014400", "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCC9000")
        /* Read Records */
        .withSimulatedCommand(
            "00B201F400", "00000000000000000000000000000000000000000000000000000000009000")
        /* Read Records */
        .withSimulatedCommand(
            "00B2014C00", "00000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000")
        /* Read Records */
        .withSimulatedCommand(
            "00B2014D00",
            "011D00000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF021D00000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF031D00000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF041D00000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000")
        /* Append Record */
        .withSimulatedCommand(
            "00E200401D00112233445566778899AABBCCDDEEFF00112233445566778899AABBCC", "9000")
        /* Append Record */
        .withSimulatedCommand(
            "00E200401D01112233445566778899AABBCCDDEEFF00112233445566778899AABBCC", "9000")
        /* Close Secure Session */
        /* no ratification asked */
        .withSimulatedCommand("008E0000040506070800", "010203049000")
        /* ratification asked */
        .withSimulatedCommand("008E8000040506070800", "010203049000")
        /* Ratification */
        .withSimulatedCommand("00B2000000", "6B00")
        .build();
  }
}
