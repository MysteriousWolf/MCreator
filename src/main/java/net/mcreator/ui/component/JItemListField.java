/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.mcreator.generator.GeneratorWrapper;
import net.mcreator.generator.mapping.MappableElement;
import net.mcreator.generator.mapping.NameMapper;
import net.mcreator.minecraft.DataListEntry;
import net.mcreator.minecraft.MCItem;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.init.BlockItemIcons;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.ui.validation.IValidable;
import net.mcreator.ui.validation.Validator;
import net.mcreator.ui.validation.component.VTextField;
import net.mcreator.ui.validation.optionpane.OptionPaneValidator;
import net.mcreator.ui.validation.optionpane.VOptionPane;
import net.mcreator.ui.validation.validators.ResourceLocationValidator;
import net.mcreator.util.FilenameUtilsPatched;
import net.mcreator.util.StringUtils;
import net.mcreator.util.image.IconUtils;
import net.mcreator.workspace.elements.ModElement;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class JItemListField<T> extends JPanel implements IValidable {

	private final TechnicalButton add = new TechnicalButton(UIRES.get("18px.add"));
	private final TechnicalButton addexternal = new TechnicalButton(UIRES.get("18px.add_new"));
	private final TechnicalButton remove = new TechnicalButton(UIRES.get("18px.remove"));
	private final TechnicalButton removeall = new TechnicalButton(UIRES.get("18px.removeall"));
	private final TechnicalButton addtag = new TechnicalButton(UIRES.get("18px.addtag"));

	private final JToggleButton include = L10N.togglebutton("elementgui.common.include");
	private final JToggleButton exclude = L10N.togglebutton("elementgui.common.exclude");

	private Validator validator = null;
	private Validator.ValidationResult currentValidationResult = null;

	private final DefaultListModel<T> elementsListModel = new DefaultListModel<>();

	protected final JList<T> elementsList = new JList<>(elementsListModel);

	protected final MCreator mcreator;

	private final List<ChangeListener> listeners = new ArrayList<>();

	private final JScrollPane pane;

	private final JComponent buttons;

	private boolean warnOnRemoveAll = false;

	protected JItemListField(MCreator mcreator) {
		this(mcreator, false);
	}

	protected JItemListField(MCreator mcreator, boolean excludeButton) {
		this.mcreator = mcreator;

		setLayout(new BorderLayout());

		elementsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		elementsList.setVisibleRowCount(1);
		elementsList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
		elementsList.setCellRenderer(new CustomListCellRenderer());

		add.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
		addexternal.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
		remove.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
		removeall.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
		addtag.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);

		add.setToolTipText(L10N.t("itemlistfield.add"));
		addexternal.setToolTipText(L10N.t("itemlistfield.addexternal"));
		remove.setToolTipText(L10N.t("itemlistfield.remove"));
		removeall.setToolTipText(L10N.t("itemlistfield.removeall"));
		addtag.setToolTipText(L10N.t("itemlistfield.addtag"));

		add.addActionListener(e -> {
			List<T> list = getElementsToAdd();
			for (T el : list)
				if (!elementsListModel.contains(el))
					elementsListModel.addElement(el);

			if (!list.isEmpty())
				this.listeners.forEach(l -> l.stateChanged(new ChangeEvent(e.getSource())));
		});

		remove.addActionListener(e -> {
			List<T> elements = elementsList.getSelectedValuesList();
			deleteElements(elements);
		});

		removeall.addActionListener(e -> {
			List<T> elements = Collections.list(elementsListModel.elements());

			if (warnOnRemoveAll && !elements.isEmpty()) {
				int result = JOptionPane.showConfirmDialog(mcreator, L10N.t("dialog.itemlistfield.deleteall"),
						L10N.t("dialog.itemlistfield.deleteall.title"), JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE);
				if (result != JOptionPane.YES_OPTION) {
					return; // if user does not agree to deletion, abort the action
				}
			}

			deleteElements(elements);
		});

		addtag.addActionListener(e -> {
			List<T> list = getTagsToAdd();
			for (T el : list)
				if (!elementsListModel.contains(el))
					elementsListModel.addElement(el);
			this.listeners.forEach(l -> l.stateChanged(new ChangeEvent(e.getSource())));
		});

		addexternal.addActionListener(e -> {
			List<T> list = getExternalElementsToAdd();
			for (T el : list)
				if (!elementsListModel.contains(el))
					elementsListModel.addElement(el);
			this.listeners.forEach(l -> l.stateChanged(new ChangeEvent(e.getSource())));
		});

		elementsList.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON2) {
					int index = elementsList.locationToIndex(e.getPoint());
					if (index >= 0)
						deleteElements(Collections.singletonList(elementsListModel.get(index)));
				} else if (e.getClickCount() == 2) {
					int index = elementsList.locationToIndex(e.getPoint());
					if (index >= 0) {
						T element = elementsListModel.get(index);
						if (element instanceof MappableElement mappableElement) {
							String unmappedValue = mappableElement.getUnmappedValue();
							if (unmappedValue.startsWith(NameMapper.MCREATOR_PREFIX)) {
								ModElement modElement = mcreator.getWorkspace()
										.getModElementByName(GeneratorWrapper.getElementPlainName(unmappedValue));
								if (modElement != null) {
									ModElementGUI<?> gui = modElement.getType()
											.getModElementGUI(mcreator, modElement, true);
									if (gui != null) {
										gui.showView();
									}
								}
							}
						}
					}
				}
			}
		});

		include.addActionListener(e -> this.listeners.forEach(l -> l.stateChanged(new ChangeEvent(e.getSource()))));
		exclude.addActionListener(e -> this.listeners.forEach(l -> l.stateChanged(new ChangeEvent(e.getSource()))));

		pane = new JScrollPane(PanelUtils.totalCenterInPanel(elementsList));
		pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		pane.setWheelScrollingEnabled(false);
		pane.addMouseWheelListener(new MouseAdapter() {
			@Override public void mouseWheelMoved(MouseWheelEvent evt) {
				int amount = evt.getScrollAmount();
				if (evt.getWheelRotation() == 1) {
					int value = pane.getHorizontalScrollBar().getValue()
							+ pane.getHorizontalScrollBar().getBlockIncrement() * amount;
					if (value > pane.getHorizontalScrollBar().getMaximum()) {
						value = pane.getHorizontalScrollBar().getMaximum();
					}
					pane.getHorizontalScrollBar().setValue(value);
				} else if (evt.getWheelRotation() == -1) {
					int value = pane.getHorizontalScrollBar().getValue()
							- pane.getHorizontalScrollBar().getBlockIncrement() * amount;
					if (value < 0) {
						value = 0;
					}
					pane.getHorizontalScrollBar().setValue(value);
				}
			}
		});

		pane.setPreferredSize(getPreferredSize());

		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		buttonsPanel.setOpaque(false);
		buttonsPanel.add(add);
		buttonsPanel.add(addtag);
		buttonsPanel.add(addexternal);
		buttonsPanel.add(remove);
		buttonsPanel.add(removeall);

		// Do not allow adding tags by default
		addtag.setVisible(false);

		// Do not allow adding external elements by default
		addexternal.setVisible(false);

		buttons = PanelUtils.totalCenterInPanel(buttonsPanel);
		buttons.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.current().getAltBackgroundColor()));
		buttons.setOpaque(true);

		if (excludeButton) {
			include.setSelected(true);
			ButtonGroup group = new ButtonGroup();
			group.add(include);
			group.add(exclude);

			include.setMargin(new Insets(0, 1, 0, 1));
			exclude.setMargin(new Insets(0, 1, 0, 1));

			JComponent incexc = PanelUtils.totalCenterInPanel(PanelUtils.join(include, exclude));
			incexc.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.current().getInterfaceAccentColor()));

			add(incexc, BorderLayout.WEST);
		}

		add(pane, BorderLayout.CENTER);
		add(buttons, BorderLayout.EAST);
	}

	public JItemListField<T> allowTags() {
		addtag.setVisible(true);
		return this;
	}

	public JItemListField<T> allowExternalElements() {
		addexternal.setVisible(true);
		return this;
	}

	public void setWarnOnRemoveAll(boolean warnOnDeleteAll) {
		this.warnOnRemoveAll = warnOnDeleteAll;
	}

	private void deleteElements(List<T> elements) {
		boolean anyRemoved = false;

		boolean deleteManaged = false;
		boolean containsManaged = elements.stream()
				.anyMatch(e -> e instanceof MappableElement mappableElement && mappableElement.isManaged());

		if (containsManaged) {
			int result = JOptionPane.showConfirmDialog(mcreator, L10N.t("dialog.itemlistfield.deletemanaged"),
					L10N.t("dialog.itemlistfield.deletemanaged.title"), JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
			if (result == JOptionPane.YES_OPTION) {
				deleteManaged = true;
			} else if (result != JOptionPane.NO_OPTION) {
				return; // if action is not yes or no, cancel deletion
			}
		}

		for (var element : elements) {
			if (element != null) {
				if (!deleteManaged && element instanceof MappableElement mappableElement && mappableElement.isManaged())
					continue; // Managed elements are only delete if deleteManaged is true

				elementsListModel.removeElement(element);
				anyRemoved = true;
			}
		}

		if (anyRemoved) {
			this.listeners.forEach(l -> l.stateChanged(new ChangeEvent(this)));
		}
	}

	public void hideButtons() {
		buttons.setVisible(false);
	}

	public void disableItemCentering() {
		Box verticalBox = Box.createVerticalBox();
		verticalBox.add(Box.createVerticalGlue());
		verticalBox.add(elementsList);
		verticalBox.add(Box.createVerticalGlue());
		elementsList.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		pane.setViewportView(verticalBox);
	}

	protected abstract List<T> getElementsToAdd();

	protected List<T> getTagsToAdd() {
		return List.of();
	}

	protected List<T> getExternalElementsToAdd() {
		String element = VOptionPane.showInputDialog(mcreator, L10N.t("itemlistfield.addexternal.message"),
				L10N.t("itemlistfield.addexternal.title"), null, new OptionPaneValidator() {
					@Override public Validator.ValidationResult validate(JComponent component) {
						String text = ((VTextField) component).getText();
						if (!text.contains(":") || text.startsWith("minecraft:") || text.startsWith("mod:")
								|| text.startsWith(mcreator.getWorkspaceSettings().getModID() + ":")) {
							return new Validator.ValidationResult(Validator.ValidationResultType.ERROR,
									L10N.t("itemlistfield.addexternal.entry.error"));
						}
						return new ResourceLocationValidator<>(L10N.t("itemlistfield.addexternal.entry"),
								(VTextField) component, true).validate();
					}
				});
		if (element != null) {
			T mappedElement = fromExternalToElement(element);
			if (mappedElement != null)
				return List.of(mappedElement);
		}
		return List.of();
	}

	@Nullable protected T fromExternalToElement(String external) {
		return null;
	}

	@Override public void setEnabled(boolean enabled) {
		add.setEnabled(enabled);
		addexternal.setEnabled(enabled);
		remove.setEnabled(enabled);
		removeall.setEnabled(enabled);
		addtag.setEnabled(enabled);
		include.setEnabled(enabled);
		exclude.setEnabled(enabled);
		elementsList.setEnabled(enabled);
	}

	public void addChangeListener(ChangeListener changeListener) {
		this.listeners.add(changeListener);
	}

	public List<T> getListElements() {
		List<T> retval = new ArrayList<>();
		for (int i = 0; i < elementsListModel.size(); i++) {
			T element = elementsListModel.get(i);
			if (element instanceof MappableElement mappableElement && !mappableElement.isValidReference())
				continue;
			retval.add(element);
		}
		return retval;
	}

	public void setListElements(@Nullable List<T> elements) {
		if (elements == null)
			return;

		elementsListModel.removeAllElements();
		for (T element : elements) {
			if (element instanceof MappableElement mappableElement && !mappableElement.isValidReference())
				continue;
			elementsListModel.addElement(element);
		}
	}

	public void addListElement(T element) {
		if (!elementsListModel.contains(element)) {
			elementsListModel.addElement(element);
			this.listeners.forEach(l -> l.stateChanged(new ChangeEvent(this)));
		}
	}

	public boolean isExclusionMode() {
		return exclude.isSelected();
	}

	public void setExclusionMode(boolean isExcluded) {
		exclude.setSelected(isExcluded);
		include.setSelected(!isExcluded);
	}

	private static final ImageIcon WARNING_ICON = IconUtils.resize(UIRES.get("18px.warning"), 13, 13);
	private static final ImageIcon ERROR_ICON = IconUtils.resize(UIRES.get("18px.remove"), 13, 13);
	private static final ImageIcon OK_ICON = IconUtils.resize(UIRES.get("18px.ok"), 13, 13);

	@Override public void paint(Graphics g) {
		super.paint(g);

		if (currentValidationResult != null) {
			g.setColor(currentValidationResult.getValidationResultType().getColor());
			if (currentValidationResult.getValidationResultType() == Validator.ValidationResultType.WARNING) {
				WARNING_ICON.paintIcon(this, g, 0, 0);
			} else if (currentValidationResult.getValidationResultType() == Validator.ValidationResultType.ERROR) {
				ERROR_ICON.paintIcon(this, g, 0, 0);
			} else if (currentValidationResult.getValidationResultType() == Validator.ValidationResultType.PASSED) {
				OK_ICON.paintIcon(this, g, 0, 0);
			}

			if (currentValidationResult.getValidationResultType() == Validator.ValidationResultType.ERROR
					|| currentValidationResult.getValidationResultType() == Validator.ValidationResultType.WARNING) {
				g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			}
		}
	}

	@Override public Validator.ValidationResult getValidationStatus() {
		Validator.ValidationResult validationResult = validator == null ? null : validator.validateIfEnabled(this);

		this.currentValidationResult = validationResult;

		//repaint as new validation status might have to be rendered
		repaint();

		return validationResult;
	}

	@Override public void setValidator(Validator validator) {
		this.validator = validator;
	}

	@Override public Validator getValidator() {
		return validator;
	}

	class CustomListCellRenderer extends JLabel implements ListCellRenderer<T> {

		@Override
		public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected,
				boolean cellHasFocus) {
			setOpaque(true);
			setBackground(isSelected ? Theme.current().getForegroundColor() : Theme.current().getBackgroundColor());
			setForeground(
					isSelected ? Theme.current().getSecondAltBackgroundColor() : Theme.current().getForegroundColor());
			if (isSelected) {
				setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.current().getBackgroundColor()),
						BorderFactory.createEmptyBorder(2, 5, 2, 5)));
			} else {
				setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.current().getBackgroundColor()),
						BorderFactory.createCompoundBorder(
								BorderFactory.createLineBorder(Theme.current().getAltBackgroundColor(), 1),
								BorderFactory.createEmptyBorder(1, 4, 1, 4))));
			}
			setHorizontalAlignment(SwingConstants.CENTER);
			setVerticalAlignment(SwingConstants.CENTER);

			setIcon(null);

			if (value instanceof MappableElement mappableElement) {
				if (!isSelected && mappableElement.isManaged()) {
					setBackground(Theme.current().getAltBackgroundColor());
				}

				Optional<DataListEntry> dataListEntryOpt = mappableElement.getDataListEntry();
				if (dataListEntryOpt.isPresent()) {
					DataListEntry dataListEntry = dataListEntryOpt.get();
					setText(dataListEntry.getReadableName());
					if (dataListEntry.getTexture() != null) {
						setIcon(IconUtils.resize(BlockItemIcons.getIconForItem(dataListEntry.getTexture()), 18));
					}
				} else {
					String unmappedValue = mappableElement.getUnmappedValue();
					setText(unmappedValue.replace(NameMapper.MCREATOR_PREFIX, "").replace("Blocks.", "")
							.replace("Items.", "").replace("#", "").replace("EXTERNAL:", ""));

					if (unmappedValue.startsWith(NameMapper.MCREATOR_PREFIX) || unmappedValue.startsWith("EXTERNAL:"))
						setIcon(IconUtils.resize(MCItem.getBlockIconBasedOnName(mcreator.getWorkspace(), unmappedValue),
								18));
					else if (unmappedValue.startsWith("#"))
						setIcon(IconUtils.resize(MCItem.TAG_ICON, 18));
				}
			} else if (value instanceof File) {
				setText(FilenameUtilsPatched.removeExtension(((File) value).getName()));
			} else {
				setText(StringUtils.machineToReadableName(value.toString().replace(NameMapper.MCREATOR_PREFIX, "")));

				if (value.toString().contains(NameMapper.MCREATOR_PREFIX))
					setIcon(IconUtils.resize(MCItem.getBlockIconBasedOnName(mcreator.getWorkspace(), value.toString()),
							18));
			}

			return this;
		}
	}

}
