package de.metas.handlingunits.inout.impl;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.model.IContextAware;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.compiere.model.I_C_DocType;
import org.compiere.model.I_C_UOM;
import org.compiere.model.I_M_Product;
import org.compiere.model.X_C_DocType;
import org.compiere.util.Env;
import org.slf4j.Logger;

import de.metas.adempiere.form.terminal.TerminalException;
import de.metas.document.IDocTypeDAO;
import de.metas.handlingunits.CompositeDocumentLUTUConfigurationHandler;
import de.metas.handlingunits.IDocumentLUTUConfigurationHandler;
import de.metas.handlingunits.IHUAssignmentBL;
import de.metas.handlingunits.IHUContext;
import de.metas.handlingunits.IHUContextFactory;
import de.metas.handlingunits.IHandlingUnitsBL;
import de.metas.handlingunits.allocation.ILUTUProducerAllocationDestination;
import de.metas.handlingunits.empties.impl.EmptiesInOutProducer;
import de.metas.handlingunits.impl.DocumentLUTUConfigurationManager;
import de.metas.handlingunits.impl.IDocumentLUTUConfigurationManager;
import de.metas.handlingunits.inout.IHUInOutBL;
import de.metas.handlingunits.inout.IHUInOutDAO;
import de.metas.handlingunits.inout.IReturnsInOutProducer;
import de.metas.handlingunits.model.I_C_OrderLine;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_HU_LUTU_Configuration;
import de.metas.handlingunits.model.I_M_HU_PI;
import de.metas.handlingunits.model.I_M_HU_PI_Item_Product;
import de.metas.handlingunits.model.I_M_InOut;
import de.metas.handlingunits.model.I_M_InOutLine;
import de.metas.inoutcandidate.spi.impl.HUPackingMaterialDocumentLineCandidate;
import de.metas.logging.LogManager;
import de.metas.materialtracking.IMaterialTrackingAttributeBL;
import de.metas.materialtracking.model.I_M_Material_Tracking;
import de.metas.quantity.Quantity;

public class HUInOutBL implements IHUInOutBL
{
	private final IDocumentLUTUConfigurationHandler<I_M_InOutLine> lutuConfigurationHandler = CustomerReturnLUTUConfigurationHandler.instance;
	private final IDocumentLUTUConfigurationHandler<List<I_M_InOutLine>> lutuConfigurationListHandler = new CompositeDocumentLUTUConfigurationHandler<>(lutuConfigurationHandler);

	private static final transient Logger logger = LogManager.getLogger(HUInOutBL.class);

	@Override
	public void updatePackingMaterialInOutLine(final de.metas.inout.model.I_M_InOutLine inoutLine,
			final HUPackingMaterialDocumentLineCandidate candidate)
	{
		Check.assumeNotNull(inoutLine, "inoutLine not null");
		Check.assumeNotNull(candidate, "candidate not null");

		final I_M_InOutLine inoutLineHU = InterfaceWrapperHelper.create(inoutLine, I_M_InOutLine.class);

		final I_M_Product product = candidate.getM_Product();
		final int productId = product.getM_Product_ID();
		final I_C_UOM uom = candidate.getC_UOM();
		final BigDecimal qtyEntered = candidate.getQty();
		final BigDecimal qty = candidate.getQtyInStockingUOM();
		final I_M_Material_Tracking materialTracking = candidate.getM_MaterialTracking();

		inoutLineHU.setM_Material_Tracking(materialTracking); // task 07734
		inoutLineHU.setM_Product_ID(productId);
		inoutLineHU.setC_UOM_ID(uom.getC_UOM_ID());
		inoutLineHU.setQtyEntered(qtyEntered);
		inoutLineHU.setMovementQty(qty);
		inoutLineHU.setIsPackagingMaterial(true);

		// task 09502: we set the M_Material_Tracking_ID, so let's also update the ASI, to have it all consistent.
		final IMaterialTrackingAttributeBL materialTrackingAttributeBL = Services.get(IMaterialTrackingAttributeBL.class);
		materialTrackingAttributeBL.createOrUpdateMaterialTrackingASI(inoutLineHU, materialTracking);

		// NOTE: packing material lines shall have no order line set (07969). This will prevent generating ICs.
		inoutLineHU.setC_OrderLine(null);

		InterfaceWrapperHelper.save(inoutLineHU);
	}

	@Override
	public void recreatePackingMaterialLines(final org.compiere.model.I_M_InOut inout)
	{
		final HUShipmentPackingMaterialLinesBuilder packingMaterialLinesBuilder = createHUShipmentPackingMaterialLinesBuilder(inout);

		final boolean deleteExistingPackingLines = true; // delete existing packing material lines, if any
		packingMaterialLinesBuilder.setOverrideExistingPackingMaterialLines(deleteExistingPackingLines);
		packingMaterialLinesBuilder.build();
	}

	@Override
	public void createPackingMaterialLines(final org.compiere.model.I_M_InOut inout)
	{
		final HUShipmentPackingMaterialLinesBuilder packingMaterialLinesBuilder = createHUShipmentPackingMaterialLinesBuilder(inout);
		packingMaterialLinesBuilder.setOverrideExistingPackingMaterialLines(false);
		packingMaterialLinesBuilder.build();
	}

	@Override
	public final HUShipmentPackingMaterialLinesBuilder createHUShipmentPackingMaterialLinesBuilder(final org.compiere.model.I_M_InOut shipment)
	{
		final HUShipmentPackingMaterialLinesBuilder packingMaterialLinesBuilder = new HUShipmentPackingMaterialLinesBuilder();
		packingMaterialLinesBuilder.setM_InOut(shipment);
		return packingMaterialLinesBuilder;
	}

	@Override
	public IReturnsInOutProducer createEmptiesInOutProducer(final Properties ctx)
	{
		return new EmptiesInOutProducer(ctx);
	}

	@Override
	public I_M_HU_PI getTU_HU_PI(final I_M_InOutLine inoutLine)
	{
		//
		// Get TU PI to use
		final I_M_HU_PI_Item_Product piItemProduct;
		if (inoutLine.getM_HU_PI_Item_Product_ID() > 0)
		{
			piItemProduct = inoutLine.getM_HU_PI_Item_Product();
		}
		else
		{
			// fallback
			// FIXME: this is a nasty workaround
			// Ideally would by to have M_HU_PI_Item_Product in receipt line
			final I_C_OrderLine orderLine = InterfaceWrapperHelper.create(inoutLine.getC_OrderLine(), I_C_OrderLine.class);
			if (orderLine == null)
			{
				logger.warn("Cannot get orderline from inout line: {}", inoutLine);
				return null;
			}
			piItemProduct = orderLine.getM_HU_PI_Item_Product();
		}
		if (piItemProduct == null)
		{
			logger.warn("Cannot get PI Item Product from inout line: {}", inoutLine);
			return null;
		}

		final I_M_HU_PI tuPI = piItemProduct.getM_HU_PI_Item().getM_HU_PI_Version().getM_HU_PI();
		return tuPI;
	}

	@Override
	public void destroyHUs(final org.compiere.model.I_M_InOut inout)
	{
		Check.assumeNotNull(inout, "inout not null");

		// services
		final IHUInOutDAO huInOutDAO = Services.get(IHUInOutDAO.class);
		final IHandlingUnitsBL handlingUnitsBL = Services.get(IHandlingUnitsBL.class);
		final IHUContextFactory huContextFactory = Services.get(IHUContextFactory.class);

		//
		// Get inout's assigned HUs
		final List<I_M_HU> hus = huInOutDAO.retrieveHandlingUnits(inout);
		if (hus.isEmpty())
		{
			return;
		}

		// TODO: make sure HUs were not touched! i.e. nobody took out some quantity, split, joined etc

		//
		// Create and configure the huContext for destroying the HUs
		final IContextAware context = InterfaceWrapperHelper.getContextAware(inout);
		final IHUContext huContext = huContextFactory.createMutableHUContextForProcessing(context);
		// If we deal with a receipt, we shall collect (and move back to Gebinde lager), only those packing materials that we own.
		if (!inout.isSOTrx())
		{
			huContext.getHUPackingMaterialsCollector().setCollectIfOwnPackingMaterialsOnly(true);
		}

		//
		// Mark assigned HUs as destroyed
		handlingUnitsBL.markDestroyed(huContext, hus);
	}

	@Override
	public void updateEffectiveValues(final I_M_InOutLine shipmentLine)
	{
		// avoid a huge development mistake
		Check.assume(shipmentLine.getM_InOut().isSOTrx(), "{} is a shipment line and not a receipt line", shipmentLine);

		// Skip packing materials line
		if (shipmentLine.isPackagingMaterial())
		{
			return;
		}

		final BigDecimal qtyCU_Effective;
		final BigDecimal qtyTU_Effective;
		final I_M_HU_PI_Item_Product piItemProduct_Effective;
		if (shipmentLine.isManualPackingMaterial())
		{
			qtyCU_Effective = shipmentLine.getQtyEntered(); // keep it as it is
			qtyTU_Effective = shipmentLine.getQtyTU_Override();
			piItemProduct_Effective = shipmentLine.getM_HU_PI_Item_Product_Override();
		}
		else
		{
			qtyCU_Effective = shipmentLine.getQtyCU_Calculated();
			qtyTU_Effective = shipmentLine.getQtyTU_Calculated();
			piItemProduct_Effective = shipmentLine.getM_HU_PI_Item_Product_Calculated();
		}

		shipmentLine.setQtyEntered(qtyCU_Effective);
		shipmentLine.setQtyEnteredTU(qtyTU_Effective);
		shipmentLine.setM_HU_PI_Item_Product(piItemProduct_Effective);
	}

	@Override
	public List<I_M_InOut> createVendorReturnInOutForHUs(final List<I_M_HU> hus, final Timestamp movementDate)
	{
		return MultiVendorHUReturnsInOutProducer.newInstance()
				.setMovementDate(movementDate)
				.addHUsToReturn(hus)
				.create();
	}

	@Override
	public IDocumentLUTUConfigurationManager createLUTUConfigurationManager(List<I_M_InOutLine> inOutLines)
	{
		Check.assumeNotEmpty(inOutLines, "inOutLines not empty");
		if (inOutLines.size() == 1)
		{
			final I_M_InOutLine inOutLine = inOutLines.get(0);
			return createLUTUConfigurationManager(inOutLine);
		}
		else
		{
			return new DocumentLUTUConfigurationManager<>(inOutLines, lutuConfigurationListHandler);
		}
	}

	@Override
	public IDocumentLUTUConfigurationManager createLUTUConfigurationManager(I_M_InOutLine inOutLine)
	{
		return new DocumentLUTUConfigurationManager<>(inOutLine, lutuConfigurationHandler);
	}

	@Override
	public boolean isCustomerReturn(final org.compiere.model.I_M_InOut inOut)
	{

		// in the case of returns the docSubType is null
		final String docSubType = IDocTypeDAO.DOCSUBTYPE_NONE;

		final I_C_DocType returnsDocType = Services.get(IDocTypeDAO.class)
				.getDocTypeOrNullForSOTrx(
						Env.getCtx() // ctx
						, X_C_DocType.DOCBASETYPE_MaterialReceipt // doc basetype
						, docSubType // doc subtype
						, true // isSOTrx
						, inOut.getAD_Client_ID() // client
						, inOut.getAD_Org_ID() // org
						, ITrx.TRXNAME_None); // trx

		if (returnsDocType == null)
		{
			// there is no customer return doc type defined in the project. Return false by default
			return false;
		}

		if (returnsDocType.getC_DocType_ID() != inOut.getC_DocType_ID())
		{
			// the inout is not a customer return
			return false;
		}

		// the inout is a customer return
		return true;
	}

	@Override
	public void createHUsForCustomerReturn(final I_M_InOutLine customerReturnLine)
	{
		final org.compiere.model.I_M_InOut customerReturn = customerReturnLine.getM_InOut();

		Check.assume(isCustomerReturn(customerReturn), "Inout {} is not a customer return ", customerReturn);

		final IContextAware ctxAware = InterfaceWrapperHelper.getContextAware(customerReturn);

		//
		// Create HU generator
		final CustomerReturnLineHUGenerator huGenerator = CustomerReturnLineHUGenerator.newInstance(ctxAware)
				.addM_InOutLine(customerReturnLine);

		//
		// Get/Create and Edit LU/TU configuration
		final IDocumentLUTUConfigurationManager lutuConfigurationManager = huGenerator.getLUTUConfigurationManager();
		final I_M_HU_LUTU_Configuration lutuConfigurationEffective = lutuConfigurationManager.getCreateLUTUConfiguration();

		//
		// No configuration => user cancelled => don't open editor
		if (lutuConfigurationEffective == null)
		{
			return;
		}

		InterfaceWrapperHelper.save(lutuConfigurationEffective, ITrx.TRXNAME_None);
		customerReturnLine.setM_HU_LUTU_Configuration(lutuConfigurationEffective);

		// huGenerator.generateAllPlanningHUs_InChunks();

		// //lutuConfigurationEffective.setQtyLU(BigDecimal.ONE);
		// InterfaceWrapperHelper.save(lutuConfigurationEffective);
		// customerReturnLine.setM_HU_LUTU_Configuration(lutuConfigurationEffective);
		//
		// Calculate the target CUs that we want to allocate
		final ILUTUProducerAllocationDestination lutuProducer = huGenerator.getLUTUProducerAllocationDestination();
		final Quantity qtyCUsTotal = lutuProducer.calculateTotalQtyCU();
		if (qtyCUsTotal.isInfinite())
		{
			throw new TerminalException("LU/TU configuration is resulting to infinite quantity: " + lutuConfigurationEffective);
		}
		huGenerator.setQtyToAllocateTarget(qtyCUsTotal);

		//
		// Generate the HUs
		final List<I_M_HU> hus = huGenerator.generate();
		Services.get(IHandlingUnitsBL.class).setHUStatusActive(hus);
		for (final I_M_HU hu : hus)
		{

			Services.get(IHUAssignmentBL.class).assignHU(customerReturnLine, hu, ITrx.TRXNAME_ThreadInherited);
		}
	}

	@Override
	public void createHUsForCustomerReturn(org.compiere.model.I_M_InOut customerReturn)
	{
		// TODO Auto-generated method stub

	}
}
