package info.kgeorgiy.ja.karpov.student;

import info.kgeorgiy.java.advanced.student.*;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StudentDB implements AdvancedQuery {
    private static final Comparator<Student> COMP_STUDENT_BY_NAME =
            Comparator.comparing(Student::getLastName).reversed()
                    .thenComparing(Comparator.comparing(Student::getFirstName).reversed().thenComparingInt(Student::getId));

    private String getFullName(Student st) {
        return st.getFirstName() + " " + st.getLastName();
    }
    private <T, C extends Collection<T>> C generalMap(Collection<Student> students, Function<Student, T> func,
                                                      Supplier<C> collector) {
        return students.stream().map(func).collect(Collectors.toCollection(collector));
    }

    private <T> List<T> listMap(Collection<Student> students, Function<Student, T> func) {
        return generalMap(students, func, ArrayList::new);
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return listMap(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return listMap(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return listMap(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return listMap(students, this::getFullName);
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return generalMap(students, Student::getFirstName, TreeSet::new);
    }

    public <S, T> T generalGetMax(Collection<S> objects, Comparator<S> comp, Function<S, T> func, T def) {
        return objects.stream().max(comp).map(func).orElse(def);
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return generalGetMax(students, Comparator.naturalOrder(), Student::getFirstName, "");
    }

    private <T> List<T> generalSort(Collection<T> objs, Comparator<T> comp) {
        return objs.stream().sorted(comp).toList();
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return generalSort(students, Comparator.naturalOrder());
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return generalSort(students, COMP_STUDENT_BY_NAME);
    }

    private <T> List<Student> generalFind(Collection<Student> students, Function<Student, T> func, T obj) {
        return students.stream().filter((Student st) -> func.apply(st).equals(obj)).toList();
    }

    private <T> List<Student> generalFindListSortByName(Collection<Student> students,
                                                        Function<Student, T> func, T obj) {
        return sortStudentsByName(generalFind(students, func, obj));
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return generalFindListSortByName(students, Student::getFirstName, name);
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return generalFindListSortByName(students, Student::getLastName, name);
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return generalFindListSortByName(students, Student::getGroup, group);
    }

    // :NOTE: использовать стандартную функцию минимума
    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return findStudentsByGroup(students, group).stream()
                .collect(Collectors.toMap(Student::getLastName, Student::getFirstName,
                        BinaryOperator.minBy(String::compareTo)));
    }

    private List<Group> generalGetGroup(Collection<Student> students,
                                        Comparator<Group> groupComp, Comparator<Student> studentComp) {
        return students.stream().map(Student::getGroup).distinct()
                .map((GroupName name) -> new Group(name, generalSort(findStudentsByGroup(students, name), studentComp)))
                .sorted(groupComp).toList();
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return generalGetGroup(students, Comparator.comparing(Group::getName), COMP_STUDENT_BY_NAME);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return generalGetGroup(students, Comparator.comparing(Group::getName), Comparator.comparingInt(Student::getId));
    }

    private GroupName generalLargestGroup(Collection<Student> students,
                                          Function<List<Student>, List<Student>> studentFunc, Comparator<Group> comp) {
        return generalGetMax(getGroupsByName(students)
                        .stream()
                        .map((Group g) -> new Group(g.getName(), studentFunc.apply(g.getStudents())))
                        .toList(),
                Comparator.comparingInt((Group g) -> g.getStudents().size())
                        .thenComparing(comp),
                Group::getName, null);
    }

    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return generalLargestGroup(students, Function.identity(), Comparator.comparing(Group::getName));
    }

    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return generalLargestGroup(students, list ->
                        getDistinctFirstNames(list).stream()
                                .map(name -> findStudentsByFirstName(students, name).get(0))
                                .toList(),
                Comparator.comparing(Group::getName).reversed());
    }

    private long getGroupCountWithName(List<Group> groups, String name) {
        return groups.stream()
                .filter(g -> g.getStudents().stream().anyMatch(st -> st.getFirstName().equals(name))).count();
    }

    private String getMPNProxy(List<Group> groups, Collection<Student> students) {
        return generalGetMax(getDistinctFirstNames((List<Student>) students),
                Comparator.comparing((String name) -> getGroupCountWithName(groups, name))
                        .thenComparing(Comparator.reverseOrder()), Function.identity(), "");
    }

    @Override
    public String getMostPopularName(Collection<Student> students) {
        return getMPNProxy(getGroupsByName(students), students);
    }

    private Map<Integer, Student> getStudentMap(Collection<Student> students) {
        return students.stream().collect(Collectors.toMap(Student::getId, Function.identity()));
    }

    private <T> List<T>  generalGetById(Map<Integer, Student> studentMap, Function<Student, T> func, final int[] ids) {
        return IntStream.of(ids).mapToObj(id -> func.apply(studentMap.get(id))).toList();
    }

    @Override
    public List<String> getFirstNames(Collection<Student> students, final int[] ids) {
        return generalGetById(getStudentMap(students), Student::getFirstName, ids);
    }

    @Override
    public List<String> getLastNames(Collection<Student> students, final int[] ids) {
        return generalGetById(getStudentMap(students), Student::getLastName, ids);
    }

    @Override
    public List<GroupName> getGroups(Collection<Student> students, final int[] ids) {
        return generalGetById(getStudentMap(students), Student::getGroup, ids);
    }

    @Override
    public List<String> getFullNames(Collection<Student> students, final int[] ids) {
        return generalGetById(getStudentMap(students), this::getFullName, ids);
    }
}
