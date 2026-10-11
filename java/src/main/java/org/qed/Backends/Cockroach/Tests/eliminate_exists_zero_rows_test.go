package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateExistsZeroRows(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "EliminateExistsZeroRows")
	outer, _ := e.scan("u", 1)
	col := e.f.Metadata().AddColumn("empty", types.Int)
	empty := e.empty(col)
	before := e.f.ConstructSelect(outer, e.filters(e.exists(empty)))
	want := e.f.ConstructSelect(outer, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)
	inner, _ := e.scan("t", 1)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(inner))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.f.ConstructNot(e.exists(empty)))))
}

func TestSQLEliminateExistsZeroRows(t *testing.T) {
	expectSQLFiresWith(t, "EliminateExistsZeroRows",
		"SELECT * FROM t WHERE EXISTS (SELECT 1 FROM u WHERE false)",
		sqlHelpers{names: []string{"SimplifyZeroCardinalityGroup", "EliminateProject", "Prune*"}})
}
