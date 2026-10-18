package rulescript_test

import "testing"

func TestUnionEliminator(t *testing.T) {
	rule := loadRule(t, "UnionEliminator")
	e := newEnv(t)
	input, cols := e.scan("t", 3)
	e.expectAbsorbed(rule, input)

	values := e.intValues(cols, []int{1, 2, 3}, []int{4, 5, 6})
	e.expectAbsorbed(rule, values)
}
