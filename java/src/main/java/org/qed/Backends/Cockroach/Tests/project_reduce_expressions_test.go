package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectReduceExpressions(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectReduceExpressions")
	input, cols := e.scan("t", 2)
	outCol := e.f.Metadata().AddColumn("out", types.Bool)
	before := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructAnd(e.f.ConstructFalse(), e.f.ConstructNe(e.v(cols[0]), e.v(cols[1]))), outCol),
	}, opt.ColSet{})
	want := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), outCol),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	nearMissCol := e.f.Metadata().AddColumn("near_miss", types.Bool)
	nearMiss := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructAnd(e.f.ConstructTrue(), e.f.ConstructNe(e.v(cols[0]), e.v(cols[1]))), nearMissCol),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
