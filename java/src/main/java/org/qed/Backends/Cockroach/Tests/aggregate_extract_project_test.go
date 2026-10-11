package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateExtractProject(t *testing.T) {
	rule := loadRule(t, "AggregateExtractProject")
	e := newEnv(t)
	a, ac := e.scan("t", 3)
	b, _ := e.scan("u", 2)
	c, cc := e.scan("u", 1)
	ab := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	base := e.f.ConstructInnerJoin(ab, c, nil, &memo.JoinPrivate{})
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(ac[1])), fcol)}
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(ac[0])}
	before := e.f.ConstructGroupBy(base, aggs, private)
	project := e.f.ConstructProject(base, nil, opt.MakeColSet(ac[0], ac[1]))
	want := e.f.ConstructGroupBy(project, aggs, private)
	e.expectRewrite(rule, before, want)

	leftOuter := e.f.ConstructLeftJoin(ab, c, nil, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(leftOuter, aggs, private))

	cond := e.f.ConstructInnerJoin(ab, c,
		e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(cond, aggs, private))

	wide, _ := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructInnerJoin(ab, wide, nil, &memo.JoinPrivate{}), aggs, private))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructInnerJoin(a, c, nil, &memo.JoinPrivate{}), aggs, private))
}
