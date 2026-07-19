import java.util.*;
import java.util.stream.Collectors;

class Employee {
    private int id;
    private String name;
    private String department;
    private double salary;

    public Employee(int id, String name, String department, double salary) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.salary = salary;
    }

    public String getDepartment() {
        return department;
    }

    public double getSalary() {
        return salary;
    }

    @Override
    public String toString() {
        return id + " " + name + " " + department + " " + salary;
    }
}

public class Solution {

    public static void main(String[] args) {

        List<Employee> employees = Arrays.asList(
                new Employee(1, "John", "IT", 90000),
                new Employee(2, "Alice", "HR", 70000),
                new Employee(3, "Bob", "IT", 120000),
                new Employee(4, "David", "IT", 100000),
                new Employee(5, "Emma", "HR", 80000),
                new Employee(6, "Mike", "IT", 95000)
        );

        String department = "IT";
        int topN = 3;

        List<Employee> result = employees.stream()
                .filter(emp -> emp.getDepartment().equalsIgnoreCase(department))
                .sorted(Comparator.comparing(Employee::getSalary).reversed())
                .limit(topN)
                .collect(Collectors.toList());

        result.forEach(System.out::println);
    }
}