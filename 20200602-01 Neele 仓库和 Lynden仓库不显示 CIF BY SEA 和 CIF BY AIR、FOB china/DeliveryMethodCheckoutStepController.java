/*
 * [y] hybris Platform
 *
 * Copyright (c) 2017 SAP SE or an SAP affiliate company.  All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package com.acerchem.storefront.controllers.pages.checkout.steps;

import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import com.acerchem.facades.facades.AcerchemCheckoutFacade;
import com.acerchem.facades.facades.AcerchemOrderException;
import com.acerchem.facades.facades.AcerchemTrayFacade;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.PreValidateCheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.PreValidateQuoteCheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.checkout.steps.AbstractCheckoutStepController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.order.CartModel;
import com.acerchem.storefront.controllers.ControllerConstants;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Iterator;
import de.hybris.platform.util.Config;
import javax.annotation.Resource;
import org.apache.log4j.Logger;


@Controller
@RequestMapping(value = "/checkout/multi/delivery-method")
public class DeliveryMethodCheckoutStepController extends AbstractCheckoutStepController
{
	private static final Logger LOG = Logger.getLogger(DeliveryMethodCheckoutStepController.class);
	private static final String DELIVERY_METHOD = "delivery-method";

	@Resource(name = "defaultAcerchemCheckoutFacade")
	private AcerchemCheckoutFacade acerchemCheckoutFacade;

	@Resource
	private AcerchemTrayFacade acerchemTrayFacade;

	@RequestMapping(value = "/choose", method = RequestMethod.GET)
	@RequireHardLogIn
	@Override
	@PreValidateQuoteCheckoutStep
	@PreValidateCheckoutStep(checkoutStep = DELIVERY_METHOD)
	public String enterStep(final Model model, final RedirectAttributes redirectAttributes) throws CMSItemNotFoundException
	{
		// Try to set default delivery mode
//		getCheckoutFacade().setDeliveryModeIfAvailable();

		final CartData cartData = acerchemCheckoutFacade.getCheckoutCart();
		model.addAttribute("cartData", cartData);
		try {
			//shaun AVIDA仓库去除ddp fca 20200426
			List<? extends DeliveryModeData> deliveryModesList = acerchemCheckoutFacade.getAllDeliveryModes();
			Iterator<? extends DeliveryModeData> it = deliveryModesList.iterator();
			String pointOfService = cartData.getEntries().get(0).getDeliveryPointOfService().getName();
			while (it.hasNext()) {
				DeliveryModeData deliveryModeData = it.next();
				if("AVIDA".equalsIgnoreCase(pointOfService)){
					if("DELIVERY_GROSS".equals(deliveryModeData.getCode())||"DELIVERY_MENTION".equals(deliveryModeData.getCode())){
						it.remove();
					}
				}

				if("Neele".equalsIgnoreCase(pointOfService)||"Lynden".equalsIgnoreCase(pointOfService)){
					if("DELIVERY_CBA".equals(deliveryModeData.getCode())||"DELIVERY_CBS".equals(deliveryModeData.getCode())||"DELIVERY_FC".equals(deliveryModeData.getCode())){
						it.remove();
					}
				}

			}
			model.addAttribute("deliveryMethods", deliveryModesList);
		} catch (AcerchemOrderException e) {
//			model.addAttribute("errorMsg",e.getMessage());
			GlobalMessages.addErrorMessage(model, e.getMessage());
		}
		this.prepareDataForPage(model);
		storeCmsPageInModel(model, getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY,
				getResourceBreadcrumbBuilder().getBreadcrumbs("checkout.multi.deliveryMethod.breadcrumb"));
		model.addAttribute("metaRobots", "noindex,nofollow");
		setCheckoutStepLinksForModel(model, getCheckoutStep());

		return ControllerConstants.Views.Pages.MultiStepCheckout.ChooseDeliveryMethodPage;
	}

	/**
	 * This method gets called when the "Use Selected Delivery Method" button is clicked. It sets the selected delivery
	 * mode on the checkout facade and reloads the page highlighting the selected delivery Mode.
	 *
	 * @param selectedDeliveryMethod
	 *           - the id of the delivery mode.
	 * @return - a URL to the page to load.
	 */
	@RequestMapping(value = "/select", method = RequestMethod.GET)
	@RequireHardLogIn
	public String doSelectDeliveryMode(@RequestParam("delivery_method") final String selectedDeliveryMethod,final Model model)
	{
		try
		{
			if (StringUtils.isNotEmpty(selectedDeliveryMethod))
			{
				acerchemCheckoutFacade.setDeliveryMode(selectedDeliveryMethod);
				final CartData cartData = acerchemCheckoutFacade.getCheckoutCart();
				LOG.info("shaun:cartData.getTotalPrice"+cartData.getTotalPrice().getValue());
				model.addAttribute("paymentInfos", acerchemCheckoutFacade.getSupportedCardTypes(selectedDeliveryMethod));
				model.addAttribute("cartData", cartData);
				//发货日期时间段
				model.addAttribute("minDelivereyDays",Config.getInt("cart.delivereyDays.min",2));
				model.addAttribute("maxDelivereyDays",Config.getInt("cart.delivereyDays.max",9));
				CartModel cartModel = acerchemCheckoutFacade.getCartModel();
				//model.addAttribute("delivereyDays",acerchemTrayFacade.getDeliveryDaysForCart(cartModel));//根据地址算出运送时间
				//shaun 2020-04-07 远期库存在选DDP的时候，可选择时间往后推迟5天，不应该推迟5天，需要检查一下为什么推迟了5天,,需要加上Future Inventory Days的时间，
				if("DELIVERY_GROSS".equals(selectedDeliveryMethod)){
					model.addAttribute("delivereyDays",cartData.getDeliveryDays());
				}else if("DELIVERY_CBA".equals(selectedDeliveryMethod)||"DELIVERY_CBS".equals(selectedDeliveryMethod)){
					model.addAttribute("delivereyDays",cartData.getDeliveryDays());
				}else{
					model.addAttribute("delivereyDays",acerchemTrayFacade.getDeliveryDaysForCart(cartModel));//根据地址算出运送时间
				}
			}
		}catch (Exception e) {
			LOG.error(e.getMessage(),e);
		}

		return getCheckoutStep().nextStep();
	}

	@RequestMapping(value = "/back", method = RequestMethod.GET)
	@RequireHardLogIn
	@Override
	public String back(final RedirectAttributes redirectAttributes)
	{
		return getCheckoutStep().previousStep();
	}

	@RequestMapping(value = "/next", method = RequestMethod.GET)
	@RequireHardLogIn
	@Override
	public String next(final RedirectAttributes redirectAttributes)
	{
		return getCheckoutStep().nextStep();
	}

	protected CheckoutStep getCheckoutStep()
	{
		return getCheckoutStep(DELIVERY_METHOD);
	}
}
