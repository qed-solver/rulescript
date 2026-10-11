package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateExistsProject(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "EliminateExistsProject")
	outer, _ := e.scan("u", 1)
	inner, ic := e.scan("t", 1)
	computed := e.f.Metadata().AddColumn("computed", types.Int)
	project := e.f.ConstructProject(inner,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(ic[0])), computed)},
		opt.ColSet{})
	before := e.f.ConstructSelect(outer, e.filters(e.exists(project)))
	want := e.f.ConstructSelect(outer, e.filters(e.exists(inner)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(inner))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.f.ConstructNot(e.exists(project)))))
}

func TestSQLEliminateExistsProject(t *testing.T) {
	expectSQLFires(t, "EliminateExistsProject", "SELECT x FROM u WHERE EXISTS (SELECT -a FROM t)")
}
