package com.voxsar.jinglesbarcodescannerhelper

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.voxsar.jinglesbarcodescannerhelper.databinding.ActivityMainBinding
import com.voxsar.jinglesbarcodescannerhelper.databinding.DialogSettingsBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsStore: SettingsStore
    private val apiService = ApiService()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private var settings = AppSettings()
    private var rootLocations: List<LocationNode> = emptyList()
    private var branchOptions: List<LocationNode> = emptyList()
    private var floorOptions: List<LocationNode> = emptyList()
    private var shelfOptions: List<LocationNode> = emptyList()
    private var boxOptions: List<LocationNode> = emptyList()

    private var selectedBranch: LocationNode? = null
    private var selectedFloor: LocationNode? = null
    private var selectedShelf: LocationNode? = null
    private var selectedBox: LocationNode? = null
    private var isUpdatingSpinners = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsStore = SettingsStore(this)
        settings = settingsStore.load()

        setupDateField(binding.expiryDateEditText)
        setupDateField(binding.manufactureDateEditText)
        setupSpinner(binding.branchSpinner) { position ->
            selectedBranch = branchOptions.getOrNull(position - 1)
            selectedFloor = null
            selectedShelf = null
            selectedBox = null
            updateLocationSelectors()
        }
        setupSpinner(binding.floorSpinner) { position ->
            selectedFloor = floorOptions.getOrNull(position - 1)
            selectedShelf = null
            selectedBox = null
            updateLocationSelectors()
        }
        setupSpinner(binding.shelfSpinner) { position ->
            selectedShelf = shelfOptions.getOrNull(position - 1)
            selectedBox = null
            updateLocationSelectors()
        }
        setupSpinner(binding.boxSpinner) { position ->
            selectedBox = boxOptions.getOrNull(position - 1)
        }

        binding.settingsButton.setOnClickListener { showSettingsDialog() }
        binding.loadLocationsButton.setOnClickListener { loadLocations() }
        binding.submitButton.setOnClickListener { submitScan() }
        binding.barcodeEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                true
            } else {
                false
            }
        }

        refreshSettingsStatus()
        updateLocationSelectors()
        binding.barcodeEditText.requestFocus()
    }

    private fun setupDateField(editText: EditText) {
        editText.setOnClickListener {
            val initialDate = editText.text.toString().takeIf { value -> value.isNotBlank() }
                ?.let(LocalDate::parse)
                ?: LocalDate.now()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                    editText.setText(dateFormatter.format(selectedDate))
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth,
            ).show()
        }
        editText.setOnLongClickListener {
            editText.setText("")
            true
        }
    }

    private fun setupSpinner(spinner: Spinner, onSelected: (Int) -> Unit) {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingSpinners) {
                    onSelected(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(LayoutInflater.from(this))
        dialogBinding.submissionUrlEditText.setText(settings.submissionUrl)
        dialogBinding.locationsUrlEditText.setText(settings.locationsUrl)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings))
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                settings = AppSettings(
                    submissionUrl = dialogBinding.submissionUrlEditText.text.toString(),
                    locationsUrl = dialogBinding.locationsUrlEditText.text.toString(),
                )
                settingsStore.save(settings)
                refreshSettingsStatus()
                toast(R.string.submission_saved)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshSettingsStatus() {
        val status = buildString {
            append("Submit URL: ")
            append(if (settings.submissionUrl.isBlank()) "not set" else settings.submissionUrl)
            append('\n')
            append("Locations URL: ")
            append(if (settings.locationsUrl.isBlank()) "not set" else settings.locationsUrl)
        }
        binding.configurationStatusText.text = status
    }

    private fun loadLocations() {
        if (settings.locationsUrl.isBlank()) {
            toast(R.string.locations_url_missing)
            return
        }
        binding.resultText.text = getString(R.string.loading_locations)
        binding.loadLocationsButton.isEnabled = false
        Thread {
            runCatching { apiService.fetchLocations(settings.locationsUrl) }
                .onSuccess { locations ->
                    runOnUiThread {
                        binding.loadLocationsButton.isEnabled = true
                        rootLocations = locations
                        selectedBranch = null
                        selectedFloor = null
                        selectedShelf = null
                        selectedBox = null
                        updateLocationSelectors()
                        binding.resultText.text = if (locations.isEmpty()) {
                            getString(R.string.no_locations_returned)
                        } else {
                            getString(R.string.locations_loaded)
                        }
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        binding.loadLocationsButton.isEnabled = true
                        binding.resultText.text = error.message ?: getString(R.string.send_failed)
                    }
                }
        }.start()
    }

    private fun submitScan() {
        val barcode = binding.barcodeEditText.text.toString().trim()
        if (barcode.isBlank()) {
            toast(R.string.barcode_required)
            return
        }
        if (settings.submissionUrl.isBlank()) {
            toast(R.string.submission_url_missing)
            return
        }

        val payload = SubmissionPayloadFactory.create(
            barcode = barcode,
            expireDate = binding.expiryDateEditText.text?.toString(),
            manufactureDate = binding.manufactureDateEditText.text?.toString(),
            itemQuantity = binding.quantityEditText.text?.toString(),
            locationPath = selectedLocationPath(),
        ) 

        binding.submitButton.isEnabled = false
        binding.resultText.text = getString(R.string.submitting_scan)
        Thread {
            runCatching { apiService.submit(settings.submissionUrl, payload) }
                .onSuccess { result ->
                    runOnUiThread {
                        binding.submitButton.isEnabled = true
                        binding.resultText.text = result.message
                        if (result.isSuccessful) {
                            toast(R.string.send_success)
                            binding.barcodeEditText.setText("")
                            binding.quantityEditText.setText("")
                            binding.barcodeEditText.requestFocus()
                        } else {
                            toast(R.string.send_failed)
                        }
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        binding.submitButton.isEnabled = true
                        binding.resultText.text = error.message ?: getString(R.string.send_failed)
                        toast(R.string.send_failed)
                    }
                }
        }.start()
    }

    private fun updateLocationSelectors() {
        branchOptions = when {
            rootLocations.any { it.type == LocationType.BRANCH } -> rootLocations.filter { it.type == LocationType.BRANCH }
            else -> emptyList()
        }
        if (branchOptions.isEmpty()) {
            selectedBranch = null
        }

        val baseForFloors = selectedBranch?.children ?: rootLocations
        floorOptions = when {
            baseForFloors.any { it.type == LocationType.FLOOR } -> baseForFloors.filter { it.type == LocationType.FLOOR }
            else -> emptyList()
        }
        if (floorOptions.isEmpty()) {
            selectedFloor = null
        }

        val baseForShelves = when {
            selectedFloor != null -> selectedFloor?.children.orEmpty()
            selectedBranch != null -> selectedBranch?.children.orEmpty()
            else -> rootLocations
        }
        shelfOptions = when {
            baseForShelves.any { it.type == LocationType.SHELF } -> baseForShelves.filter { it.type == LocationType.SHELF }
            else -> emptyList()
        }
        if (shelfOptions.isEmpty()) {
            selectedShelf = null
        }

        val baseForBoxes = when {
            selectedShelf != null -> selectedShelf?.children.orEmpty()
            selectedFloor != null -> selectedFloor?.children.orEmpty()
            else -> rootLocations
        }
        boxOptions = when {
            baseForBoxes.any { it.type == LocationType.BOX } -> baseForBoxes.filter { it.type == LocationType.BOX }
            else -> emptyList()
        }
        if (boxOptions.isEmpty()) {
            selectedBox = null
        }

        bindSpinner(
            spinner = binding.branchSpinner,
            container = binding.branchContainer,
            placeholder = getString(R.string.select_branch),
            options = branchOptions,
            selectedNode = selectedBranch,
        )
        bindSpinner(
            spinner = binding.floorSpinner,
            container = binding.floorContainer,
            placeholder = getString(R.string.select_floor),
            options = floorOptions,
            selectedNode = selectedFloor,
        )
        bindSpinner(
            spinner = binding.shelfSpinner,
            container = binding.shelfContainer,
            placeholder = getString(R.string.select_shelf),
            options = shelfOptions,
            selectedNode = selectedShelf,
        )
        bindSpinner(
            spinner = binding.boxSpinner,
            container = binding.boxContainer,
            placeholder = getString(R.string.select_box),
            options = boxOptions,
            selectedNode = selectedBox,
        )
    }

    private fun bindSpinner(
        spinner: Spinner,
        container: View,
        placeholder: String,
        options: List<LocationNode>,
        selectedNode: LocationNode?,
    ) {
        container.visibility = if (options.isEmpty()) View.GONE else View.VISIBLE
        if (options.isEmpty()) {
            spinner.adapter = null
            return
        }

        val items = buildList {
            add(placeholder)
            addAll(options.map { node -> node.name })
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        isUpdatingSpinners = true
        spinner.adapter = adapter
        val selectedIndex = options.indexOfFirst { it.id == selectedNode?.id }
        spinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0, false)
        isUpdatingSpinners = false
    }

    private fun selectedLocationPath(): List<LocationNode> = listOfNotNull(
        selectedBranch,
        selectedFloor,
        selectedShelf,
        selectedBox,
    )

    private fun toast(messageRes: Int) {
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
    }
}
