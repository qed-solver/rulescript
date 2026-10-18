package rulescript_test

import (
	"context"
	"fmt"
	"os"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/settings/cluster"
	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/norm"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/rulescript"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/testutils/testcat"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/eval"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
	"github.com/cockroachdb/cockroach/pkg/testutils/datapathutils"
)

type env struct {
	t       *testing.T
	evalCtx eval.Context
	cat     *testcat.Catalog
	f       norm.Factory
}

func newEnv(t *testing.T) *env {
	e := &env{t: t, cat: testcat.New()}
	e.evalCtx = eval.MakeTestingEvalContext(cluster.MakeTestingClusterSettings())
	for _, ddl := range []string{
		"CREATE TABLE t (a INT PRIMARY KEY, b INT, c INT)",
		"CREATE TABLE u (x INT PRIMARY KEY, y INT)",
		"CREATE TABLE nn (a INT PRIMARY KEY, b INT NOT NULL)",
		"CREATE TABLE nn3 (a INT PRIMARY KEY, b INT NOT NULL, c INT NOT NULL)",
	} {
		if _, err := e.cat.ExecuteDDL(ddl); err != nil {
			t.Fatal(err)
		}
	}
	e.f.Init(context.Background(), &e.evalCtx, e.cat)
	e.f.DisableOptimizations()
	return e
}

func loadRule(t *testing.T, name string) *rulescript.Rule {
	data, err := os.ReadFile(datapathutils.TestDataPath(t, "rules", name+".json"))
	if err != nil {
		t.Fatal(err)
	}
	rule, err := rulescript.Parse(data)
	if err != nil {
		t.Fatal(err)
	}
	return rule
}

func (e *env) scan(table string, ncols int) (memo.RelExpr, []opt.ColumnID) {
	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, tree.Name(table))
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	cols := make([]opt.ColumnID, ncols)
	for i := range cols {
		cols[i] = tabID.ColumnID(i)
	}
	return e.f.ConstructScan(&memo.ScanPrivate{Table: tabID, Cols: opt.MakeColSet(cols...)}), cols
}

func (e *env) v(c opt.ColumnID) opt.ScalarExpr { return e.f.ConstructVariable(c) }

func (e *env) empty(cols ...opt.ColumnID) memo.RelExpr {
	return e.f.ConstructValues(nil, &memo.ValuesPrivate{
		Cols: append(opt.ColList{}, cols...),
		ID:   e.f.Metadata().NextUniqueID(),
	})
}

func (e *env) num(n int) opt.ScalarExpr {
	return e.f.ConstructConst(tree.NewDInt(tree.DInt(n)), types.Int)
}

func (e *env) filters(conds ...opt.ScalarExpr) memo.FiltersExpr {
	out := make(memo.FiltersExpr, len(conds))
	for i, c := range conds {
		out[i] = e.f.ConstructFiltersItem(c)
	}
	return out
}

func (e *env) format(r opt.Expr) string {
	var sb strings.Builder
	canon(&sb, r)
	return sb.String()
}

func canon(sb *strings.Builder, x opt.Expr) {
	switch t := x.(type) {
	case *memo.ScanExpr:
		fmt.Fprintf(sb, "(scan %v)", t.Cols)
		return
	case *memo.ValuesExpr:
		fmt.Fprintf(sb, "(values cols=%v", t.Private().(*memo.ValuesPrivate).Cols)
		for _, row := range t.Rows {
			sb.WriteString(" ")
			canon(sb, row)
		}
		sb.WriteString(")")
		return
	case *memo.VariableExpr:
		fmt.Fprintf(sb, "(var %d)", t.Col)
		return
	case *memo.ConstExpr:
		fmt.Fprintf(sb, "(const %s)", t.Value.String())
		return
	case *memo.ProjectionsItem:
		fmt.Fprintf(sb, "(item %d ", t.Col)
		canon(sb, t.Element)
		sb.WriteString(")")
		return
	case *memo.ProjectExpr:
		fmt.Fprintf(sb, "(project pass=%v ", t.Passthrough)
		canon(sb, t.Input)
		sb.WriteString(" ")
		canon(sb, &t.Projections)
		sb.WriteString(")")
		return
	}
	sb.WriteString("(" + x.Op().String())
	if grouping, ok := x.Private().(*memo.GroupingPrivate); ok {
		fmt.Fprintf(sb, " group=%v", grouping.GroupingCols)
	}
	for i, n := 0, x.ChildCount(); i < n; i++ {
		sb.WriteString(" ")
		canon(sb, x.Child(i))
	}
	sb.WriteString(")")
}

func (e *env) expectRewrite(rule *rulescript.Rule, before, want memo.RelExpr) {
	e.t.Helper()
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		e.t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	if g, w := e.format(got), e.format(want); g != w {
		e.t.Fatalf("%s produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			rule.Name, e.format(before), g, w)
	}
}

func (e *env) expectRewriteUpToFreshColumns(rule *rulescript.Rule, before, want memo.RelExpr) {
	e.t.Helper()
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		e.t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	g, _ := alphaComputedColumns(e.format(got))
	w, _ := alphaComputedColumns(e.format(want))
	if g != w {
		e.t.Fatalf("%s produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			rule.Name, e.format(before), g, w)
	}
}

var computedItemID = regexp.MustCompile(`\(item ([0-9]+) `)

func alphaComputedColumns(plan string) (string, int) {
	matches := computedItemID.FindAllStringSubmatch(plan, -1)
	seen := make(map[string]string)
	for _, match := range matches {
		id := match[1]
		if _, ok := seen[id]; ok {
			continue
		}
		label := fmt.Sprintf("fresh%d", len(seen)+1)
		seen[id] = label
	}
	for id, label := range seen {
		plan = strings.ReplaceAll(plan, "(item "+id+" ", "(item "+label+" ")
		plan = strings.ReplaceAll(plan, "(var "+id+")", "(var "+label+")")
	}
	plan = groupList.ReplaceAllStringFunc(plan, func(match string) string {
		sub := groupList.FindStringSubmatch(match)
		var labels []string
		for _, part := range strings.FieldsFunc(sub[1], func(r rune) bool { return r == ',' }) {
			if lo, hi, ok := strings.Cut(part, "-"); ok {
				from, _ := strconv.Atoi(lo)
				to, _ := strconv.Atoi(hi)
				for id := from; id <= to; id++ {
					labels = append(labels, labelFor(seen, strconv.Itoa(id)))
				}
				continue
			}
			labels = append(labels, labelFor(seen, part))
		}
		sort.Strings(labels)
		return "group=(" + strings.Join(labels, ",") + ")"
	})
	return plan, len(seen)
}

var groupList = regexp.MustCompile(`group=\(([0-9,\-]*)\)`)

func labelFor(seen map[string]string, id string) string {
	if label, ok := seen[id]; ok {
		return label
	}
	return id
}

func (e *env) expectAbsorbed(rule *rulescript.Rule, plan memo.RelExpr) {
	e.t.Helper()
	got, ok := rule.Rewrite(&e.f, plan)
	if !ok {
		e.t.Fatalf("%s did not match:\n%s", rule.Name, e.format(plan))
	}
	if got != plan {
		e.t.Fatalf("%s rewrote the plan instead of leaving it unchanged:\n%s\nproducing:\n%s",
			rule.Name, e.format(plan), e.format(got))
	}
}

var columnListToken = regexp.MustCompile(`\(var (\d+)\)|\(item (\d+) |pass=\(([0-9,\-]*)\)|\(scan \(([0-9,\-]*)\)\)|cols=\[([0-9 ]*)\]|group=\(([0-9,\-]*)\)`)

func canonicalColumns(plan string) string {
	seen := make(map[string]string)
	name := func(id string) string {
		if label, ok := seen[id]; ok {
			return label
		}
		label := fmt.Sprintf("c%d", len(seen)+1)
		seen[id] = label
		return label
	}
	list := func(text, sep string) string {
		var labels []string
		for _, part := range strings.FieldsFunc(text, func(r rune) bool { return r == ',' || r == ' ' }) {
			if lo, hi, ok := strings.Cut(part, "-"); ok {
				from, _ := strconv.Atoi(lo)
				to, _ := strconv.Atoi(hi)
				for id := from; id <= to; id++ {
					labels = append(labels, name(strconv.Itoa(id)))
				}
				continue
			}
			labels = append(labels, name(part))
		}
		return strings.Join(labels, sep)
	}
	return columnListToken.ReplaceAllStringFunc(plan, func(match string) string {
		sub := columnListToken.FindStringSubmatch(match)
		switch {
		case sub[1] != "":
			return "(var " + name(sub[1]) + ")"
		case sub[2] != "":
			return "(item " + name(sub[2]) + " "
		case strings.HasPrefix(match, "pass="):
			return "pass=(" + list(sub[3], ",") + ")"
		case strings.HasPrefix(match, "(scan"):
			return "(scan (" + list(sub[4], ",") + "))"
		case strings.HasPrefix(match, "group="):
			labels := strings.Split(list(sub[6], ","), ",")
			sort.Strings(labels)
			return "group=(" + strings.Join(labels, ",") + ")"
		default:
			return "cols=[" + list(sub[5], " ") + "]"
		}
	})
}

func (e *env) expectRewriteUpToColumnNames(rule *rulescript.Rule, before, want memo.RelExpr) {
	e.t.Helper()
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		e.t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	if g, w := canonicalColumns(e.format(got)), canonicalColumns(e.format(want)); g != w {
		e.t.Fatalf("%s produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			rule.Name, e.format(before), g, w)
	}
}

func (e *env) expectNoMatch(rule *rulescript.Rule, plan memo.RelExpr) {
	e.t.Helper()
	if got, ok := rule.Apply(&e.f, plan); ok {
		e.t.Fatalf("%s applied to a plan it does not match:\n%s\nproducing:\n%s",
			rule.Name, e.format(plan), e.format(got))
	}
}
