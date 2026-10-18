package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateExistsProject(t *testing.T) {
	rule := loadRule(t, "EliminateExistsProject")
	e := newEnv(t)
	outer, _ := e.scan("u", 1)
	input, ic := e.scan("t", 3)
	f := e.f.Metadata().AddColumn("f", types.Int)
	projections := memo.ProjectionsExpr{{Element: e.v(ic[1]), Col: f}}
	projected := e.f.ConstructProject(input, projections, opt.ColSet{})
	before := e.f.ConstructSelect(outer, e.filters(e.exists(projected)))
	want := e.f.ConstructSelect(outer, e.filters(e.exists(input)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(input))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.f.ConstructNot(e.exists(projected)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(e.f.ConstructSelect(projected, e.filters(e.f.ConstructTrue()))))))
}
