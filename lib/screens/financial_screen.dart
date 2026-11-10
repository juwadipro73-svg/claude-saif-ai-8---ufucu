import 'dart:math' as math;
import 'package:flutter/material.dart';

class FinancialScreen extends StatefulWidget {
  const FinancialScreen({Key? key}) : super(key: key);

  @override
  State<FinancialScreen> createState() => _FinancialScreenState();
}

class _FinancialScreenState extends State<FinancialScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // EMI State
  double _principal = 500000;
  double _annualRate = 8.5;
  int _years = 5;
  double _monthlyEmi = 0;
  double _totalInterest = 0;
  double _totalPayment = 0;

  // GST State
  double _gstAmount = 1000;
  double _gstPercent = 18;
  bool _isGstExclusive = true;
  double _netAmount = 0;
  double _taxAmount = 0;
  double _grossAmount = 0;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _computeEmi();
    _computeGst();
  }

  void _computeEmi() {
    if (_principal <= 0 || _annualRate <= 0 || _years <= 0) return;
    double monthlyRate = (_annualRate / 12) / 100;
    int months = _years * 12;
    double emi = (_principal * monthlyRate * math.pow(1 + monthlyRate, months)) /
        (math.pow(1 + monthlyRate, months) - 1);

    setState(() {
      _monthlyEmi = emi;
      _totalPayment = emi * months;
      _totalInterest = _totalPayment - _principal;
    });
  }

  void _computeGst() {
    setState(() {
      if (_isGstExclusive) {
        _netAmount = _gstAmount;
        _taxAmount = (_gstAmount * _gstPercent) / 100;
        _grossAmount = _netAmount + _taxAmount;
      } else {
        _grossAmount = _gstAmount;
        _netAmount = (_gstAmount * 100) / (100 + _gstPercent);
        _taxAmount = _grossAmount - _netAmount;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TabBar(
          controller: _tabController,
          labelColor: Colors.tealAccent,
          tabs: const [
            Tab(text: 'Loan EMI'),
            Tab(text: 'GST / Tax'),
          ],
        ),
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              _buildEmiTab(),
              _buildGstTab(),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildEmiTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            initialValue: _principal.toStringAsFixed(0),
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Loan Amount (₹ / \$)', border: OutlineInputBorder()),
            onChanged: (v) { _principal = double.tryParse(v) ?? 0; _computeEmi(); },
          ),
          const SizedBox(height: 12),
          TextFormField(
            initialValue: _annualRate.toString(),
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Annual Interest Rate (%)', border: OutlineInputBorder()),
            onChanged: (v) { _annualRate = double.tryParse(v) ?? 0; _computeEmi(); },
          ),
          const SizedBox(height: 12),
          TextFormField(
            initialValue: _years.toString(),
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Tenure (Years)', border: OutlineInputBorder()),
            onChanged: (v) { _years = int.tryParse(v) ?? 1; _computeEmi(); },
          ),
          const SizedBox(height: 20),
          Card(
            color: Colors.blueGrey.shade900,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  _resultRow('Monthly EMI', '₹${_monthlyEmi.toStringAsFixed(2)}', isHighlight: true),
                  const Divider(),
                  _resultRow('Total Interest', '₹${_totalInterest.toStringAsFixed(2)}'),
                  const Divider(),
                  _resultRow('Total Payment', '₹${_totalPayment.toStringAsFixed(2)}'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGstTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            initialValue: _gstAmount.toStringAsFixed(0),
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Amount (₹ / \$)', border: OutlineInputBorder()),
            onChanged: (v) { _gstAmount = double.tryParse(v) ?? 0; _computeGst(); },
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<double>(
            value: _gstPercent,
            decoration: const InputDecoration(labelText: 'GST Rate', border: OutlineInputBorder()),
            items: const [
              DropdownMenuItem(value: 5.0, child: Text('5%')),
              DropdownMenuItem(value: 12.0, child: Text('12%')),
              DropdownMenuItem(value: 18.0, child: Text('18%')),
              DropdownMenuItem(value: 28.0, child: Text('28%')),
            ],
            onChanged: (v) { if (v != null) { _gstPercent = v; _computeGst(); } },
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: RadioListTile<bool>(
                  title: const Text('Exclusive', style: TextStyle(fontSize: 12)),
                  value: true,
                  groupValue: _isGstExclusive,
                  onChanged: (v) { setState(() { _isGstExclusive = v!; _computeGst(); }); },
                ),
              ),
              Expanded(
                child: RadioListTile<bool>(
                  title: const Text('Inclusive', style: TextStyle(fontSize: 12)),
                  value: false,
                  groupValue: _isGstExclusive,
                  onChanged: (v) { setState(() { _isGstExclusive = v!; _computeGst(); }); },
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Card(
            color: Colors.blueGrey.shade900,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  _resultRow('Net Amount', '₹${_netAmount.toStringAsFixed(2)}'),
                  const Divider(),
                  _resultRow('GST Tax', '₹${_taxAmount.toStringAsFixed(2)}'),
                  const Divider(),
                  _resultRow('Gross Total', '₹${_grossAmount.toStringAsFixed(2)}', isHighlight: true),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _resultRow(String label, String val, {bool isHighlight = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          Text(
            val,
            style: TextStyle(
              fontSize: isHighlight ? 18 : 15,
              fontWeight: FontWeight.bold,
              color: isHighlight ? Colors.tealAccent : Colors.white,
            ),
          ),
        ],
      ),
    );
  }
}
