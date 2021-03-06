package de.metas.handlingunits.pporder.api.impl;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


import java.util.Properties;

import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Services;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_UOM;
import org.compiere.model.I_M_Product;

import de.metas.handlingunits.IHUPIItemProductDAO;
import de.metas.handlingunits.allocation.ILUTUConfigurationFactory;
import de.metas.handlingunits.impl.AbstractDocumentLUTUConfigurationHandler;
import de.metas.handlingunits.model.I_M_HU_LUTU_Configuration;
import de.metas.handlingunits.model.I_M_HU_PI_Item_Product;
import de.metas.handlingunits.model.I_PP_Order;
import de.metas.handlingunits.model.I_PP_Order_BOMLine;
import de.metas.material.planning.pporder.IPPOrderBOMBL;
import de.metas.material.planning.pporder.PPOrderUtil;

/**
 * Handles {@link I_PP_Order_BOMLine} lines from which we are able to receive materials
 *
 * @author tsa
 *
 */
/* package */class PPOrderBOMLineDocumentLUTUConfigurationHandler extends AbstractDocumentLUTUConfigurationHandler<I_PP_Order_BOMLine>
{
	public static final transient PPOrderBOMLineDocumentLUTUConfigurationHandler instance = new PPOrderBOMLineDocumentLUTUConfigurationHandler();

	private PPOrderBOMLineDocumentLUTUConfigurationHandler()
	{
		super();
	}

	@Override
	public I_M_HU_LUTU_Configuration createNewLUTUConfiguration(final I_PP_Order_BOMLine ppOrderBOMLine)
	{
		PPOrderUtil.assertReceipt(ppOrderBOMLine);

		final org.eevolution.model.I_PP_Order ppOrder = ppOrderBOMLine.getPP_Order();
		final I_C_BPartner bpartner = ppOrder.getC_BPartner();
		final I_M_HU_PI_Item_Product tuPIItemProduct = getM_HU_PI_Item_Product(ppOrderBOMLine);
		final I_M_Product cuProduct = ppOrderBOMLine.getM_Product();
		final I_C_UOM cuUOM = ppOrderBOMLine.getC_UOM();

		//
		// LU/TU COnfiguration
		final ILUTUConfigurationFactory lutuConfigurationFactory = Services.get(ILUTUConfigurationFactory.class);
		final I_M_HU_LUTU_Configuration lutuConfiguration = lutuConfigurationFactory.createLUTUConfiguration(tuPIItemProduct, cuProduct, cuUOM, bpartner);
		updateLUTUConfiguration(lutuConfiguration, ppOrderBOMLine);

		return lutuConfiguration;
	}

	@Override
	public I_M_HU_PI_Item_Product getM_HU_PI_Item_Product(final I_PP_Order_BOMLine ppOrderBOMLine)
	{
		final Properties ctx = InterfaceWrapperHelper.getCtx(ppOrderBOMLine);
		final I_M_HU_PI_Item_Product pipVirtual = Services.get(IHUPIItemProductDAO.class).retrieveVirtualPIMaterialItemProduct(ctx);
		return pipVirtual;
	}

	@Override
	public void updateLUTUConfiguration(final I_M_HU_LUTU_Configuration lutuConfiguration, final I_PP_Order_BOMLine documentLine)
	{
		final I_PP_Order ppOrder = InterfaceWrapperHelper.create(documentLine.getPP_Order(), I_PP_Order.class);
		PPOrderDocumentLUTUConfigurationHandler.instance.updateLUTUConfiguration(lutuConfiguration, ppOrder);
	}

	@Override
	public void setCurrentLUTUConfiguration(final I_PP_Order_BOMLine documentLine, final I_M_HU_LUTU_Configuration lutuConfiguration)
	{
		documentLine.setM_HU_LUTU_Configuration(lutuConfiguration);
	}

	@Override
	public I_M_HU_LUTU_Configuration getCurrentLUTUConfigurationOrNull(final I_PP_Order_BOMLine documentLine)
	{
		final I_M_HU_LUTU_Configuration lutuConfiguration = documentLine.getM_HU_LUTU_Configuration();
		if (lutuConfiguration == null || lutuConfiguration.getM_HU_LUTU_Configuration_ID() <= 0)
		{
			return null;
		}

		return lutuConfiguration;
	}
}
