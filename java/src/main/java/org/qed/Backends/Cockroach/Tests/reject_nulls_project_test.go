package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRejectNullsProject(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "RejectNullsProject")
	input, cols := e.scan("t", 2)
	computed := e.f.Metadata().AddColumn("computed", types.Int)
	project := e.f.ConstructProject(
		input,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(cols[0])), computed)},
		opt.MakeColSet(cols[0]),
	)
	before := e.f.ConstructSelect(project, e.filters(e.f.ConstructIsNot(e.v(computed), memo.NullSingleton)))

	filteredInput := e.f.ConstructSelect(input, e.filters(e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton)))
	wantProject := e.f.ConstructProject(
		filteredInput,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(cols[0])), computed)},
		opt.MakeColSet(cols[0]),
	)
	want := e.f.ConstructSelect(wantProject, e.filters(e.f.ConstructIsNot(e.v(computed), memo.NullSingleton)))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSelect(project, e.filters(e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton)))
	e.expectNoMatch(rule, nearMiss)
}
