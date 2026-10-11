package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateZeroCardSelect(t *testing.T) {
	rule := loadRule(t, "EliminateZeroCardSelect")
	e := newEnv(t)
	c := e.f.Metadata().AddColumn("c", types.Int)
	input := e.empty(c)
	first := e.f.ConstructSelect(input, e.filters(e.f.ConstructGt(e.v(c), e.num(0))))
	before := e.f.ConstructSelect(first, e.filters(e.f.ConstructLt(e.v(c), e.num(10))))
	e.expectRewrite(rule, before, input)

	e.expectNoMatch(rule, first)
	scan, sc := e.scan("t", 1)
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructSelect(scan, e.filters(e.f.ConstructGt(e.v(sc[0]), e.num(0)))),
		e.filters(e.f.ConstructLt(e.v(sc[0]), e.num(10)))))
	c2 := e.f.Metadata().AddColumn("c2", types.Int)
	two := e.empty(c, c2)
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructSelect(two, e.filters(e.f.ConstructGt(e.v(c), e.num(0)))),
		e.filters(e.f.ConstructLt(e.v(c), e.num(10)))))
}

func TestSQLEliminateZeroCardSelect(t *testing.T) {
	expectSQLFiresWith(t, "EliminateZeroCardSelect",
		"SELECT * FROM (SELECT * FROM (SELECT x FROM u WHERE false) WHERE x > 0) WHERE x < 10",
		sqlHelpers{limits: map[string]int{"SimplifyZeroCardinalityGroup": 1}, names: []string{"EliminateProject", "PruneValuesCols", "PruneScanCols"}})
}
