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

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import javax.annotation.Resource;
import com.acerchem.core.model.PortAndAirportModel;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.acerchem.facades.facades.AcerchemCheckoutFacade;
import com.acerchem.facades.facades.AcerchemOrderException;
import com.acerchem.facades.facades.AcerchemTrayFacade;
import com.acerchem.storefront.controllers.ControllerConstants;
import java.util.Iterator;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.PreValidateCheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.PreValidateQuoteCheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.validation.ValidationResults;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.checkout.steps.AbstractCheckoutStepController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.AddressForm;
import de.hybris.platform.acceleratorstorefrontcommons.util.AddressDataUtil;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.commercefacades.address.data.AddressVerificationResult;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commerceservices.address.AddressVerificationDecision;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.util.Config;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import com.acerchem.core.service.AcerchemStockService;


@Controller
@RequestMapping(value = "/checkout/multi/delivery-address")
public class DeliveryAddressCheckoutStepController extends AbstractCheckoutStepController
{
	private static final Logger LOG = Logger.getLogger(DeliveryAddressCheckoutStepController.class);
	private static final String DELIVERY_ADDRESS = "delivery-address";
	private static final String SHOW_SAVE_TO_ADDRESS_BOOK_ATTR = "showSaveToAddressBook";
	private static final int TYPE_ONE = 1;
	private static final int TYPE_TWO = 2;
	@Resource(name = "addressDataUtil")
	private AddressDataUtil addressDataUtil;

	@Resource(name = "defaultAcerchemCheckoutFacade")
	private AcerchemCheckoutFacade acerchemCheckoutFacade;

	@Resource
	private AcerchemTrayFacade acerchemTrayFacade;
	@Resource
	private AcerchemStockService acerchemStockService;

	@RequestMapping(value = "/add", method = RequestMethod.GET)
	@RequireHardLogIn
	@PreValidateQuoteCheckoutStep
	@PreValidateCheckoutStep(checkoutStep = DELIVERY_ADDRESS)
	public String enterStep(final Model model, final RedirectAttributes redirectAttributes) throws CMSItemNotFoundException
	{

		//港口：港口类型type=1
		final List<PortAndAirportModel> port = acerchemStockService.getPortOrAirPort(TYPE_ONE);
		//机场：机场类型type=2
		final List<PortAndAirportModel> airPort = acerchemStockService.getPortOrAirPort(TYPE_TWO);

		getCheckoutFacade().setDeliveryAddressIfAvailable();
		final CartData cartData = acerchemCheckoutFacade.getCheckoutCart();

		//shaun2020
		if(null!=port){
			cartData.setPort(port);
		}
		if(null!=airPort){
			cartData.setAirPort(airPort);
		}

		//cartData.setDeliveryAddress(null);
		try {
			if (cartData.getPickUpdate() != null){
				SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
				//shaun 2020库存天数 start
				if("DELIVERY_CBS".equals(cartData.getDeliveryMode().getCode())){
					//by sea
					Date date = new Date();
					//+X
					int X = cartData.getDeliveryDays();
					//+A
					int A = Config.getInt("supplementary.customs.declaration.days.before.delivery",4);
					Calendar c = Calendar.getInstance();
					c.setTime(date);
					c.add(Calendar.DATE, X+A);
					date = c.getTime();
					cartData.setPickUpdate(sdf1.format(date));
				}else if("DELIVERY_CBA".equals(cartData.getDeliveryMode().getCode())){
					//by air
					Date date = new Date();
					//+X
					int X = cartData.getDeliveryDays();
					//+A
					int A = Config.getInt("supplementary.customs.declaration.days.before.delivery",4);
					Calendar c = Calendar.getInstance();
					c.setTime(date);
					c.add(Calendar.DATE, X+A);
					date = c.getTime();
					cartData.setPickUpdate(sdf1.format(date));
				}else if("DELIVERY_FC".equals(cartData.getDeliveryMode().getCode())){
					//FOB CHINA
					Date date = new Date();
					//+X
					int X = cartData.getDeliveryDays();
					Calendar c = Calendar.getInstance();
					c.setTime(date);
					c.add(Calendar.DATE, X);
					date = c.getTime();
					cartData.setPickUpdate(sdf1.format(date));
				}else{
					//DDP & FCA
					//默认不是B库存
					cartData.setIsInTranssitStock(false);
					Date date = sdf1.parse(cartData.getPickUpdate());
					if(!"0".equals(cartData.getDescription())){
						//TODO
						Calendar c = Calendar.getInstance();
						c.setTime(date);
						if(cartData.getEntries().get(0).getDeliveryPointOfService().getName().equals("Neele")){
							int fixedDate = 0;
							if(cartData.getDescription().equals("2")){
								fixedDate = Config.getInt("neele.shipping.days",30)+Config.getInt("days.of.customs.clearance.and.storage",3);
								cartData.setIsInTranssitStock(true);
							}else if(cartData.getDescription().equals("1")){
								fixedDate = Config.getInt("supplementary.customs.declaration.days.before.delivery",4)+Config.getInt("neele.shipping.days",30)+Config.getInt("days.of.customs.clearance.and.storage",3);
							}
							c.add(Calendar.DATE, fixedDate);
							date = c.getTime();
						}else if(cartData.getEntries().get(0).getDeliveryPointOfService().getName().equals("Lynden")){
							int fixedDate = 0;
							if(cartData.getDescription().equals("2")){
								fixedDate = Config.getInt("lynden.shipping.days",15)+Config.getInt("days.of.customs.clearance.and.storage",3);
								cartData.setIsInTranssitStock(true);
							}else if(cartData.getDescription().equals("1")){
								fixedDate = Config.getInt("supplementary.customs.declaration.days.before.delivery",4)+Config.getInt("lynden.shipping.days",15)+Config.getInt("days.of.customs.clearance.and.storage",3);
							}
							c.add(Calendar.DATE, fixedDate);
							date = c.getTime();
						}
					}
					cartData.setPickUpdate(sdf1.format(date));
				}
				//shaun 2020库存天数 end
				String waitDelivereyDate = cartData.getPickUpdate();
				Calendar ca = Calendar.getInstance();
				try {
					ca.setTime(sdf1.parse(waitDelivereyDate));
					ca.add(Calendar.DATE, acerchemCheckoutFacade.getTotalPriceForCart(cartData));//
					waitDelivereyDate = sdf1.format(ca.getTime());
					cartData.setWaitDeliveiedDate(waitDelivereyDate);
					//Date endDate = sdf.parse(waitDelivereyDate);
					//orderModel.setWaitDeliveiedDate(endDate);
				} catch (ParseException e1) {
					// TODO Auto-generated catch block
					//						e1.printStackTrace();
					LOG.error(e1.getMessage(),e1);
				}
			}else{
				SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
				String waitDelivereyDate = cartData.getPickUpdate();
				Calendar ca = Calendar.getInstance();
				try {
					ca.setTime(new Date());
					ca.add(Calendar.DATE, Config.getInt("cart.delivereyDays.min", 2));
					ca.add(Calendar.DATE, acerchemCheckoutFacade.getTotalPriceForCart(cartData));//
					waitDelivereyDate = sdf1.format(ca.getTime());
					cartData.setWaitDeliveiedDate(waitDelivereyDate);
				} catch (Exception e1) {
					LOG.error(e1.getMessage(),e1);
				}
			}
			populateCommonModelAttributes(model, cartData, new AddressForm());
		} catch (Exception e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			LOG.error(e.getMessage(),e);
		}

		return ControllerConstants.Views.Pages.MultiStepCheckout.AddEditDeliveryAddressPage;
	}

	@RequestMapping(value = "/add", method = RequestMethod.POST)
	@RequireHardLogIn
	public String add(final AddressForm addressForm, final BindingResult bindingResult, final Model model,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{


		getAddressValidator().validate(addressForm, bindingResult);


		if (bindingResult.hasErrors())
		{
			GlobalMessages.addErrorMessage(model, "address.error.formentry.invalid");
			return ControllerConstants.Views.Pages.MultiStepCheckout.AddEditDeliveryAddressPage;
		}

		final AddressData newAddress = addressDataUtil.convertToAddressData(addressForm);

		processAddressVisibilityAndDefault(addressForm, newAddress);

		// Verify the address data.
		final AddressVerificationResult<AddressVerificationDecision> verificationResult = getAddressVerificationFacade()
				.verifyAddressData(newAddress);
		final boolean addressRequiresReview = getAddressVerificationResultHandler().handleResult(verificationResult, newAddress,
				model, redirectModel, bindingResult, getAddressVerificationFacade().isCustomerAllowedToIgnoreAddressSuggestions(),
				"checkout.multi.address.updated");
		//发货日期时间段
		model.addAttribute("minDelivereyDays",Config.getInt("cart.delivereyDays.min",2));
		model.addAttribute("maxDelivereyDays",Config.getInt("cart.delivereyDays.max",9));
		CartModel cartmodel = acerchemCheckoutFacade.getCartModel();
		model.addAttribute("delivereyDays",acerchemTrayFacade.getDeliveryDaysForCart(cartmodel));//根据地址算出运送时间



		if (addressRequiresReview)
		{
			return ControllerConstants.Views.Pages.MultiStepCheckout.AddEditDeliveryAddressPage;
		}

		getUserFacade().addAddress(newAddress);

		acerchemCheckoutFacade.setDeliveryAddress(newAddress);

		return enterStep(model, redirectModel);

	}

	protected void processAddressVisibilityAndDefault(final AddressForm addressForm, final AddressData newAddress)
	{
		if (addressForm.getSaveInAddressBook() != null)
		{
			newAddress.setVisibleInAddressBook(addressForm.getSaveInAddressBook().booleanValue());
			if (addressForm.getSaveInAddressBook().booleanValue() && getUserFacade().isAddressBookEmpty())
			{
				newAddress.setDefaultAddress(true);
			}
		}
		else if (getCheckoutCustomerStrategy().isAnonymousCheckout())
		{
			newAddress.setDefaultAddress(true);
			newAddress.setVisibleInAddressBook(true);
		}
		newAddress.setVisibleInAddressBook(true);
	}

	@RequestMapping(value = "/edit", method = RequestMethod.GET)
	@RequireHardLogIn
	public String editAddressForm(@RequestParam("editAddressCode") final String editAddressCode, final Model model,
			final RedirectAttributes redirectAttributes) throws CMSItemNotFoundException, AcerchemOrderException
	{
		final ValidationResults validationResults = getCheckoutStep().validate(redirectAttributes);
		if (getCheckoutStep().checkIfValidationErrors(validationResults))
		{
			return getCheckoutStep().onValidation(validationResults);
		}

		AddressData addressData = null;
		if (StringUtils.isNotEmpty(editAddressCode))
		{
			addressData = getCheckoutFacade().getDeliveryAddressForCode(editAddressCode);
		}

		final AddressForm addressForm = new AddressForm();
		final boolean hasAddressData = addressData != null;
		if (hasAddressData)
		{
			addressDataUtil.convert(addressData, addressForm);
		}

		final CartData cartData = getCheckoutFacade().getCheckoutCart();
		populateCommonModelAttributes(model, cartData, addressForm);

		if (addressData != null)
		{
			model.addAttribute(SHOW_SAVE_TO_ADDRESS_BOOK_ATTR, Boolean.valueOf(!addressData.isVisibleInAddressBook()));
		}

		return ControllerConstants.Views.Pages.MultiStepCheckout.AddEditDeliveryAddressPage;
	}

	@RequestMapping(value = "/edit", method = RequestMethod.POST)
	@RequireHardLogIn
	public String edit(final AddressForm addressForm, final BindingResult bindingResult, final Model model,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException, AcerchemOrderException
	{
		getAddressValidator().validate(addressForm, bindingResult);

		final CartData cartData = getCheckoutFacade().getCheckoutCart();
		populateCommonModelAttributes(model, cartData, addressForm);

		if (bindingResult.hasErrors())
		{
			GlobalMessages.addErrorMessage(model, "address.error.formentry.invalid");
			return ControllerConstants.Views.Pages.MultiStepCheckout.AddEditDeliveryAddressPage;
		}

		final AddressData newAddress = addressDataUtil.convertToAddressData(addressForm);

		processAddressVisibility(addressForm, newAddress);

		newAddress.setDefaultAddress(getUserFacade().isAddressBookEmpty() || getUserFacade().getAddressBook().size() == 1
				|| Boolean.TRUE.equals(addressForm.getDefaultAddress()));

		// Verify the address data.
		final AddressVerificationResult<AddressVerificationDecision> verificationResult = getAddressVerificationFacade()
				.verifyAddressData(newAddress);
		final boolean addressRequiresReview = getAddressVerificationResultHandler().handleResult(verificationResult, newAddress,
				model, redirectModel, bindingResult, getAddressVerificationFacade().isCustomerAllowedToIgnoreAddressSuggestions(),
				"checkout.multi.address.updated");

		if (addressRequiresReview)
		{
			if (StringUtils.isNotEmpty(addressForm.getAddressId()))
			{
				final AddressData addressData = getCheckoutFacade().getDeliveryAddressForCode(addressForm.getAddressId());
				if (addressData != null)
				{
					model.addAttribute(SHOW_SAVE_TO_ADDRESS_BOOK_ATTR, Boolean.valueOf(!addressData.isVisibleInAddressBook()));
					model.addAttribute("edit", Boolean.TRUE);
				}
			}

			return ControllerConstants.Views.Pages.MultiStepCheckout.AddEditDeliveryAddressPage;
		}

		getUserFacade().editAddress(newAddress);
//		getCheckoutFacade().setDeliveryModeIfAvailable();
		getCheckoutFacade().setDeliveryAddress(newAddress);

		return getCheckoutStep().nextStep();
	}

	protected void processAddressVisibility(final AddressForm addressForm, final AddressData newAddress)
	{

		if (addressForm.getSaveInAddressBook() == null)
		{
			newAddress.setVisibleInAddressBook(true);
		}
		else
		{
			newAddress.setVisibleInAddressBook(Boolean.TRUE.equals(addressForm.getSaveInAddressBook()));
		}
	}

	@RequestMapping(value = "/remove", method =
	{ RequestMethod.GET, RequestMethod.POST })
	@RequireHardLogIn
	public String removeAddress(@RequestParam("addressCode") final String addressCode, final RedirectAttributes redirectModel,
			final Model model) throws CMSItemNotFoundException
	{
		if (getCheckoutFacade().isRemoveAddressEnabledForCart())
		{
			final AddressData addressData = new AddressData();
			addressData.setId(addressCode);
			getUserFacade().removeAddress(addressData);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER,
					"account.confirmation.address.removed");
		}
		storeCmsPageInModel(model, getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL));
		model.addAttribute("addressForm", new AddressForm());

		return getCheckoutStep().currentStep();
	}

	@RequestMapping(value = "/select", method = RequestMethod.POST)
	@RequireHardLogIn
	public String doSelectSuggestedAddress(final AddressForm addressForm, final RedirectAttributes redirectModel,
										   @RequestParam(required = false) final String pickUpDate)
	{
		final Set<String> resolveCountryRegions = org.springframework.util.StringUtils
				.commaDelimitedListToSet(Config.getParameter("resolve.country.regions"));

		final AddressData selectedAddress = addressDataUtil.convertToAddressData(addressForm);
		final CountryData countryData = selectedAddress.getCountry();

		if (!resolveCountryRegions.contains(countryData.getIsocode()))
		{
			selectedAddress.setRegion(null);
		}

		if (addressForm.getSaveInAddressBook() != null)
		{
			selectedAddress.setVisibleInAddressBook(addressForm.getSaveInAddressBook().booleanValue());
		}

		if (Boolean.TRUE.equals(addressForm.getEditAddress()))
		{
			getUserFacade().editAddress(selectedAddress);
		}
		else
		{
			getUserFacade().addAddress(selectedAddress);
		}

		final AddressData previousSelectedAddress = getCheckoutFacade().getCheckoutCart().getDeliveryAddress();
		// Set the new address as the selected checkout delivery address
		getCheckoutFacade().setDeliveryAddress(selectedAddress);
//		if (previousSelectedAddress != null && !previousSelectedAddress.isVisibleInAddressBook())
//		{ // temporary address should be removed
//			getUserFacade().removeAddress(previousSelectedAddress);
//		}

		GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "checkout.multi.address.added");

		//保存收货时间
		acerchemCheckoutFacade.savePickUpDateForOrder(pickUpDate);

		return getCheckoutStep().nextStep();
	}


	/**
	 * This method gets called when the "Use this Address" button is clicked. It sets the selected delivery address on
	 * the checkout facade - if it has changed, and reloads the page highlighting the selected delivery address.
	 *
	 * @param selectedAddressCode
	 *           - the id of the delivery address.
	 *
	 * @return - a URL to the page to load.
	 * @throws CMSItemNotFoundException
	 * @throws AcerchemOrderException
	 */
	@RequestMapping(value = "/select", method = RequestMethod.GET)
	@RequireHardLogIn
	public String doSelectDeliveryAddress(@RequestParam("selectedAddressCode" ) final String selectedAddressCode,
										  final Model model, final RedirectAttributes redirectAttributes,
										 @RequestParam(required = false) final String selectedDeliveryModeCode,
										  @RequestParam(required = false) final String pickUpDate) throws CMSItemNotFoundException, AcerchemOrderException {
		if (StringUtils.isNotBlank(selectedAddressCode))
		{
			final AddressData selectedAddressData = getCheckoutFacade().getDeliveryAddressForCode(selectedAddressCode);

	      /*  double deliveryCost=acerchemTrayFacade.getTotalPriceForCart(cartData, selectedAddressData);
	        CartModel cartModel = acerchemCheckoutFacade.getCartModel();
	        cartData.setDeliveryCost(acerchemCheckoutFacade.createPrice(cartModel, deliveryCost));*/

			final boolean hasSelectedAddressData = selectedAddressData != null;

			boolean isValid = true;
			if (hasSelectedAddressData)
			{
				try {
					CountryData countryData = selectedAddressData.getCountry();
					acerchemCheckoutFacade.validateCartAddress(countryData);
				}catch (AcerchemOrderException e){
                    GlobalMessages.addErrorMessage(model, e.getMessage());

                    isValid= false;
				}

				if (isValid){
				    acerchemCheckoutFacade.setDeliveryAddress(selectedAddressData);
				}
			}
		}

		return enterStep(model, redirectAttributes);

	}


	@RequestMapping(value = "/region", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public List<RegionData> doSelectRegion(@RequestParam("countryIso" ) final String countryIso,
										  final Model model, final RedirectAttributes redirectAttributes
										) throws CMSItemNotFoundException {

		//return  getI18NFacade().getRegionsForCountryIso(countryIso);

		List<RegionData> regions =getI18NFacade().getRegionsForCountryIso(countryIso);

       // response.getWriter().write("123");

		return regions;


	}


    @RequestMapping(value = "/addPickUpDate", method = RequestMethod.GET)
    @RequireHardLogIn
    public String addPickUpDate(final Model model, @RequestParam(required = false) final String pickUpDate) throws CMSItemNotFoundException, AcerchemOrderException {

        //保存收货时间
        acerchemCheckoutFacade.savePickUpDateForOrder(pickUpDate);

        final CartData cartData = acerchemCheckoutFacade.getCheckoutCart();

		populateCommonModelAttributes(model, cartData, new AddressForm());

		return ControllerConstants.Views.Pages.MultiStepCheckout.AddEditDeliveryAddressPage;

	}

	protected void setDeliveryAddress(final AddressData selectedAddressData)
	{
		final AddressData cartCheckoutDeliveryAddress = getCheckoutFacade().getCheckoutCart().getDeliveryAddress();
		if (isAddressIdChanged(cartCheckoutDeliveryAddress, selectedAddressData))
		{
			getCheckoutFacade().setDeliveryAddress(selectedAddressData);
			if (cartCheckoutDeliveryAddress != null && !cartCheckoutDeliveryAddress.isVisibleInAddressBook())
			{ // temporary address should be removed
//				getUserFacade().removeAddress(cartCheckoutDeliveryAddress);
			}
		}
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

	protected String getBreadcrumbKey()
	{
		return "checkout.multi." + getCheckoutStep().getProgressBarId() + ".breadcrumb";
	}

	protected CheckoutStep getCheckoutStep()
	{
		return getCheckoutStep(DELIVERY_ADDRESS);
	}

	protected void populateCommonModelAttributes(final Model model, final CartData cartData, final AddressForm addressForm)
			throws CMSItemNotFoundException, AcerchemOrderException
	{

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

		cartData.setDeliveryMode(acerchemCheckoutFacade.getDeliveryModes());

		//model.addAttribute("deliveryMode", acerchemCheckoutFacade.getDeliveryModes());

		model.addAttribute("cartData", cartData);
		model.addAttribute("addressForm", addressForm);
		if (cartData.getDeliveryMode()!=null) {
			model.addAttribute("paymentInfos", acerchemCheckoutFacade.getSupportedCardTypes(cartData.getDeliveryMode().getCode()));
		}else{
			model.addAttribute("paymentInfos",acerchemCheckoutFacade.getSupportedCardTypes("DELIVERY_GROSS"));
		}


		if (cartData.getDeliveryMode()!=null&&"DELIVERY_MENTION".equals(cartData.getDeliveryMode().getCode())) {
			model.addAttribute("deliveryAddresses", acerchemCheckoutFacade.getDeliveryAddresses());
		}else{
			model.addAttribute("deliveryAddresses", getDeliveryAddresses(cartData.getDeliveryAddress()));


			 double deliveryCost=acerchemTrayFacade.getTotalPriceForCart(cartData, cartData.getDeliveryAddress());
			 //
/*			 CartModel cartModel = acerchemCheckoutFacade.getCartModel();
		     cartData.setDeliveryCost(acerchemCheckoutFacade.createPrice(cartModel, deliveryCost));


		     if (deliveryCost>0){

		    	 double total=0;
		    	 total= cartData.getDeliveryCost().getValue().doubleValue()+cartData.getTotalPrice().getValue().doubleValue();

		    	 cartData.setTotalPrice(acerchemCheckoutFacade.createPrice(cartModel, total));
		     }*/

		     // set the waitDelivereyDate
		     if (cartData.getPickUpdate() != null){
		     SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		     SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
				String waitDelivereyDate = cartData.getPickUpdate();
				Calendar ca = Calendar.getInstance();
				try {
					ca.setTime(sdf1.parse(waitDelivereyDate));
					ca.add(Calendar.DATE, acerchemCheckoutFacade.getTotalPriceForCart(cartData));//
					waitDelivereyDate = sdf1.format(ca.getTime());
					//shaun 20200407
					//cartData.setDeliveryDays(acerchemCheckoutFacade.getTotalPriceForCart(cartData));
					//shaun 20200417库存天数优化
					if("DELIVERY_GROSS".equals(new ArrayList<OrderEntryData>(cartData.getPickupOrderGroups().get(0).getEntries()).get(0).getDeliveryMode().getCode())){
						//用原来的天数。
					}else if("DELIVERY_CBS".equals(cartData.getDeliveryMode().getCode())){
						cartData.setDeliveryDays(cartData.getDeliveryDays()+Config.getInt("supplementary.customs.declaration.days.before.delivery",4));
					}else if("DELIVERY_CBA".equals(cartData.getDeliveryMode().getCode())){
						cartData.setDeliveryDays(cartData.getDeliveryDays()+Config.getInt("supplementary.customs.declaration.days.before.delivery",4));
					}else if("DELIVERY_FC".equals(cartData.getDeliveryMode().getCode())){

					}else{
						cartData.setDeliveryDays(acerchemCheckoutFacade.getTotalPriceForCart(cartData));
					}
					cartData.setWaitDeliveiedDate(waitDelivereyDate);
					//Date endDate = sdf.parse(waitDelivereyDate);
					//orderModel.setWaitDeliveiedDate(endDate);
				} catch (ParseException e1) {
					// TODO Auto-generated catch block
//					e1.printStackTrace();
					LOG.error(e1.getMessage(),e1);
				}

		     }
		}

		//shaun 20200430 提示未获取到运费
		if(cartData.getDeliveryCost().getValue().compareTo(BigDecimal.ZERO) <= 0 ){
			if(cartData.getDeliveryMode() != null && ("DELIVERY_GROSS".equals(cartData.getDeliveryMode().getCode())||"DELIVERY_CBA".equals(cartData.getDeliveryMode().getCode())||"DELIVERY_CBS".equals(cartData.getDeliveryMode().getCode())) )
			{
				Map<String, Object> modelMap = model.asMap();
				if (!modelMap.containsKey(GlobalMessages.ERROR_MESSAGES_HOLDER)){
					GlobalMessages.addInfoMessage(model,"Do not get the delivery cost information，please contact with I4U.");
				}
			}
		}


		model.addAttribute("noAddress", Boolean.valueOf(getCheckoutFlowFacade().hasNoDeliveryAddress()));
		model.addAttribute("addressFormEnabled", Boolean.valueOf(getCheckoutFacade().isNewAddressEnabledForCart()));
		model.addAttribute("removeAddressEnabled", Boolean.valueOf(getCheckoutFacade().isRemoveAddressEnabledForCart()));
		model.addAttribute(SHOW_SAVE_TO_ADDRESS_BOOK_ATTR, Boolean.TRUE);
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, getResourceBreadcrumbBuilder().getBreadcrumbs(getBreadcrumbKey()));
		model.addAttribute("metaRobots", "noindex,nofollow");
		//发货日期时间段
		model.addAttribute("minDelivereyDays",Config.getInt("cart.delivereyDays.min",2));
		model.addAttribute("maxDelivereyDays",Config.getInt("cart.delivereyDays.max",9));
//		CartModel cartmodel = acerchemCheckoutFacade.getCartModel();

		//TODO shaun库存天数 start
		/*if("DELIVERY_CBS".equals(cartData.getDeliveryMode().getCode())){
			//by sea
			int fixedDate = 0;
			//+X
			int X = cartData.getDeliveryDays();
			//+A
			int A = Config.getInt("supplementary.customs.declaration.days.before.delivery",4);
			fixedDate = X+A;
			cartData.setDeliveryDays(fixedDate);
		}else if("DELIVERY_CBA".equals(cartData.getDeliveryMode().getCode())){
			//by air
			int fixedDate = 0;
			//+X
			int X = cartData.getDeliveryDays();
			//+A
			int A = Config.getInt("supplementary.customs.declaration.days.before.delivery",4);
			fixedDate = X+A;
			cartData.setDeliveryDays(fixedDate);
		}else if("DELIVERY_FC".equals(cartData.getDeliveryMode().getCode())){
			//FOB CHINA
			int fixedDate = 0;
			//+X
			int X = cartData.getDeliveryDays();
			fixedDate = X;
			cartData.setDeliveryDays(fixedDate);
		}else{*/
			//DDP & FCA
			//默认不是B库存
			cartData.setIsInTranssitStock(false);
			int fixedDate = 0;
			if(cartData.getEntries().get(0).getDeliveryPointOfService().getName().equals("Neele")){
				if("2".equals(cartData.getDescription())){
					fixedDate = Config.getInt("neele.shipping.days",30)+Config.getInt("days.of.customs.clearance.and.storage",3);
					cartData.setIsInTranssitStock(true);
				}else if("1".equals(cartData.getDescription())){
					fixedDate = Config.getInt("supplementary.customs.declaration.days.before.delivery",4)+Config.getInt("neele.shipping.days",30)+Config.getInt("days.of.customs.clearance.and.storage",3);
					model.addAttribute("maxDelivereyDays",Config.getInt("china.delivereyDays.max",90));
				}
			}else if(cartData.getEntries().get(0).getDeliveryPointOfService().getName().equals("Lynden")){
				if("2".equals(cartData.getDescription())){
					fixedDate = Config.getInt("lynden.shipping.days",15)+Config.getInt("days.of.customs.clearance.and.storage",3);
					cartData.setIsInTranssitStock(true);
				}else if("1".equals(cartData.getDescription())){
					fixedDate = Config.getInt("supplementary.customs.declaration.days.before.delivery",4)+Config.getInt("lynden.shipping.days",15)+Config.getInt("days.of.customs.clearance.and.storage",3);
					model.addAttribute("maxDelivereyDays",Config.getInt("china.delivereyDays.max",90));
				}
			}
		if("DELIVERY_CBS".equals(cartData.getDeliveryMode().getCode())){
			//发货日期时间段
			model.addAttribute("minDelivereyDays",Config.getInt("sea.delivereyDays.min",0));
			model.addAttribute("maxDelivereyDays",Config.getInt("sea.delivereyDays.max",90));
		}else if("DELIVERY_CBA".equals(cartData.getDeliveryMode().getCode())){
			//发货日期时间段
			model.addAttribute("minDelivereyDays",Config.getInt("air.delivereyDays.min",0));
			model.addAttribute("maxDelivereyDays",Config.getInt("air.delivereyDays.max",90));
		}else if("DELIVERY_FC".equals(cartData.getDeliveryMode().getCode())){
			//发货日期时间段
			model.addAttribute("minDelivereyDays",Config.getInt("china.delivereyDays.min",0));
			model.addAttribute("maxDelivereyDays",Config.getInt("china.delivereyDays.max",90));
		}else{
			cartData.setDeliveryDays(cartData.getDeliveryDays()+fixedDate);
		}


		//}



		//TODO shaun库存天数 end



		model.addAttribute("delivereyDays",cartData.getDeliveryDays());//根据地址算出运送时间

		
		addressForm.setCountryIso("US");
		if (StringUtils.isNotBlank(addressForm.getCountryIso()))
		{
			model.addAttribute("regions", getI18NFacade().getRegionsForCountryIso(addressForm.getCountryIso()));
			model.addAttribute("country", getI18NFacade().getCountryForIsocode(addressForm.getCountryIso()));
		}
		prepareDataForPage(model);
		storeCmsPageInModel(model, getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL));
		setCheckoutStepLinksForModel(model, getCheckoutStep());
	}

	protected List<? extends AddressData> 	getDeliveryAddresses(final AddressData selectedAddressData) // NOSONAR
	{
		List<AddressData> deliveryAddresses = null;
//		if (selectedAddressData != null)
//		{
			deliveryAddresses = (List<AddressData>) getCheckoutFacade().getSupportedDeliveryAddresses(true);

//			if (deliveryAddresses == null || deliveryAddresses.isEmpty())
//			{
//				deliveryAddresses = Collections.singletonList(selectedAddressData);
//			}
//			else if (!isAddressOnList(deliveryAddresses, selectedAddressData))
//			{
//				deliveryAddresses.add(selectedAddressData);
//			}
//		}

		return deliveryAddresses == null ? Collections.<AddressData> emptyList() : deliveryAddresses;
	}
	
}
