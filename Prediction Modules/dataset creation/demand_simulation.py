import numpy as np
import matplotlib.pyplot as plt
import argparse

# Scenario definitions with demand growth and economic shift parameters
scenarios = {
    "best_case": {"demand_growth": 1.2, "economic_shift": 0.8},
    "worst_case": {"demand_growth": 0.7, "economic_shift": 1.3},
    "neutral": {"demand_growth": 1.0, "economic_shift": 1.0}
}

# Historical demand statistics (mean and standard deviation)
mean_demand = 500
std_dev_demand = 50

def monte_carlo_simulation(scenario, iterations=1000):
    """
    Run Monte Carlo simulation for demand based on a scenario.

    Parameters:
    - scenario: A dictionary with 'demand_growth' and 'economic_shift' parameters
    - iterations: Number of simulations to run

    Returns:
    - List of simulated demand outcomes
    """
    results = []
    for i in range(iterations):
        simulated_demand = np.random.normal(mean_demand * scenario['demand_growth'],
                                            std_dev_demand * scenario['economic_shift'])
        results.append(simulated_demand)
    return results

def risk_assessment(simulation_results, stock_level):
    """
    Assess stockout risk based on simulation results.

    Parameters:
    - simulation_results: List of simulated demand values
    - stock_level: The stock level to compare demand against

    Returns:
    - Stockout probability as a percentage
    """
    stockouts = sum([1 for result in simulation_results if result > stock_level])
    risk_probability = stockouts / len(simulation_results)
    return risk_probability * 100

def plot_results(simulation_results, scenario_name):
    plt.hist(simulation_results, bins=30, edgecolor='k', alpha=0.7)
    plt.title(f'{scenario_name.capitalize()} Demand Simulation')
    plt.xlabel('Demand')
    plt.ylabel('Frequency')
    plt.savefig(f'{scenario_name}_demand_simulation.png')  # Save the plot as an image file
    plt.close()  # Close the plot to free memory

def plot_comparison(simulation_results, scenario_names):
    fig, axs = plt.subplots(len(scenario_names), 1, figsize=(10, 10), sharex=True)
    for i, scenario_name in enumerate(scenario_names):
        axs[i].hist(simulation_results[scenario_name], bins=30, edgecolor='k', alpha=0.7)
        axs[i].set_title(f'{scenario_name.capitalize()} Demand Simulation')
        axs[i].set_xlabel('Demand')
        axs[i].set_ylabel('Frequency')
    plt.tight_layout()
    plt.savefig('comparison_demand_simulation.png')  # Save the comparison plot
    plt.close()

def parse_arguments():
    parser = argparse.ArgumentParser(description="Run demand simulation with specified scenarios.")
    parser.add_argument('--scenario', type=str, choices=list(scenarios.keys()), default='best_case',
                        help='Specify the scenario to run (default: best_case)')
    parser.add_argument('--iterations', type=int, default=1000,
                        help='Number of simulation iterations (default: 1000)')
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_arguments()
    scenario = scenarios[args.scenario]
    iterations = args.iterations

    # Define stock level
    stock_level = 600

    # Run simulation for the specified scenario
    print(f"Running Monte Carlo simulation for {args.scenario} scenario with {iterations} iterations...")
    simulation_results = monte_carlo_simulation(scenario, iterations)

    # Assess risk
    best_case_risk = risk_assessment(simulation_results, stock_level)
    print(f"{args.scenario.capitalize()} scenario stockout risk: {best_case_risk:.2f}%")

    # Plot results
    plot_results(simulation_results, args.scenario)

    # Run simulations for all scenarios and plot comparison
    all_scenario_results = {scenario_name: monte_carlo_simulation(scenario) for scenario_name, scenario in scenarios.items()}
    plot_comparison(all_scenario_results, list(all_scenario_results.keys()))
